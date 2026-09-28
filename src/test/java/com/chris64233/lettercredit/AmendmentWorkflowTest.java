package com.chris64233.lettercredit;

import com.chris64233.lettercredit.domain.AcceptanceStatus;
import com.chris64233.lettercredit.domain.AmendmentStatus;
import com.chris64233.lettercredit.domain.DocumentSummary;
import com.chris64233.lettercredit.domain.PendingPresentationPolicy;
import com.chris64233.lettercredit.domain.PresentationStatus;
import com.chris64233.lettercredit.exception.BusinessException;
import com.chris64233.lettercredit.exception.ErrorCode;
import com.chris64233.lettercredit.service.AcceptanceService;
import com.chris64233.lettercredit.service.AcceptanceView;
import com.chris64233.lettercredit.service.AmendmentService;
import com.chris64233.lettercredit.service.AmendmentView;
import com.chris64233.lettercredit.service.BalanceChangeView;
import com.chris64233.lettercredit.service.CreditBalanceView;
import com.chris64233.lettercredit.service.CreditService;
import com.chris64233.lettercredit.service.CreditVersionDiffView;
import com.chris64233.lettercredit.service.CreditVersionService;
import com.chris64233.lettercredit.service.CreditVersionView;
import com.chris64233.lettercredit.service.PresentationService;
import com.chris64233.lettercredit.service.PresentationView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 信用证修订（修改）流程的端到端业务规则测试：创建冻结、金额下限、
 * 受益人决定与接受范围、未承兑交单归属、版本不可变、幂等、取消/拒绝留痕、
 * 与承兑并发时的余额重校验，以及版本差异与余额变化查询。
 */
@SpringBootTest
class AmendmentWorkflowTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);
    private static final List<String> ALLOWED = List.of("INVOICE");

    @Autowired
    private CreditService creditService;
    @Autowired
    private PresentationService presentationService;
    @Autowired
    private AcceptanceService acceptanceService;
    @Autowired
    private AmendmentService amendmentService;
    @Autowired
    private CreditVersionService versionService;

    @Test
    void proposingFreezesCurrentVersionAndRemainingAmount() {
        String creditNo = newCredit();
        presentationService.present("P-FRZ-1", creditNo, new BigDecimal("300.0000"),
                TODAY, List.of(doc("INVOICE")));
        acceptanceService.accept("P-FRZ-1", new BigDecimal("300.0000"), null, null, "o1");

        AmendmentView a = amendmentService.propose("AM-FRZ-1", creditNo,
                new BigDecimal("1200.0000"), TODAY.plusMonths(6),
                List.of("INVOICE", "PACKING_LIST"), "applicant-1");

        assertThat(a.status()).isEqualTo(AmendmentStatus.PROPOSED);
        assertThat(a.baseVersionNo()).isEqualTo(1);
        assertThat(a.amendmentSeq()).isEqualTo(1);
        assertThat(a.changedFields()).containsExactly("ALLOWED_DOCUMENT_TYPES", "EXPIRY_DATE",
                "MAX_AMOUNT");
        assertThat(a.frozenAcceptedAmount()).isEqualByComparingTo("300.0000");
        assertThat(a.frozenAvailableAmount()).isEqualByComparingTo("700.0000");
        // 修订未决定前当前版本仍为 1，余额条款不变
        assertThat(creditService.balance(creditNo).currentVersionNo()).isEqualTo(1);
    }

    @Test
    void duplicateActiveAmendmentRejectedAndDuplicateAmendmentNoIsIdempotent() {
        String creditNo = newCredit();
        AmendmentView first = amendmentService.propose("AM-DUP-1", creditNo,
                new BigDecimal("1200.0000"), TODAY.plusMonths(6), ALLOWED, "applicant-1");

        // 同一信用证第二笔活动修订被拒
        assertThatThrownBy(() -> amendmentService.propose("AM-DUP-2", creditNo,
                new BigDecimal("900.0000"), TODAY.plusMonths(6), ALLOWED, "applicant-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACTIVE_AMENDMENT_EXISTS);

        // 修订号重复但内容一致 -> 幂等返回原修订
        AmendmentView again = amendmentService.propose("AM-DUP-1", creditNo,
                new BigDecimal("1200.0000"), TODAY.plusMonths(6), ALLOWED, "applicant-1");
        assertThat(again.amendmentSeq()).isEqualTo(first.amendmentSeq());

        // 修订号重复但内容不一致 -> 冲突
        assertThatThrownBy(() -> amendmentService.propose("AM-DUP-1", creditNo,
                new BigDecimal("1300.0000"), TODAY.plusMonths(6), ALLOWED, "applicant-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_AMENDMENT_NO);
    }

    @Test
    void amendmentWithoutAnyChangedTermsRejected() {
        String creditNo = newCredit();
        assertThatThrownBy(() -> amendmentService.propose("AM-SAME-1", creditNo,
                new BigDecimal("1000.0000"), TODAY.plusMonths(3), List.of("INVOICE"),
                "applicant-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AMENDMENT_NO_TERMS_CHANGED);
    }

    @Test
    void loweringMaxAmountBelowCumulativeAcceptedRejected() {
        String creditNo = newCredit();
        presentationService.present("P-LOW-1", creditNo, new BigDecimal("600.0000"),
                TODAY, List.of(doc("INVOICE")));
        acceptanceService.accept("P-LOW-1", new BigDecimal("600.0000"), null, null, "o1");

        assertThatThrownBy(() -> amendmentService.propose("AM-LOW-1", creditNo,
                new BigDecimal("500.0000"), TODAY.plusMonths(3), ALLOWED, "applicant-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AMENDMENT_AMOUNT_BELOW_ACCEPTED);
    }

    @Test
    void keepOldDecisionMustCoverPendingPresentationsButWithdrawDoesNot() {
        String creditNo = newCredit();
        presentationService.present("P-COV-1", creditNo, new BigDecimal("400.0000"),
                TODAY, List.of(doc("INVOICE")));
        // 已承兑 300，尚有 400 未承兑交单；最高金额降到 500 会让继续旧版本的 200 失去归属
        acceptanceService.accept("P-COV-1", new BigDecimal("300.0000"), null, null, "o1");
        presentationService.present("P-COV-2", creditNo, new BigDecimal("400.0000"),
                TODAY, List.of(doc("INVOICE")));

        // 创建不被未承兑总额拦截（500 仍高于累计已承兑 300），冻结未承兑交单数
        AmendmentView proposed = amendmentService.propose("AM-COV-1", creditNo,
                new BigDecimal("500.0000"), TODAY.plusMonths(3), ALLOWED, "applicant-1");
        assertThat(proposed.frozenPendingPresentations()).isEqualTo(1);

        // 选择继续旧版本 -> 500 无法覆盖 300 已承兑 + 400 未承兑，拒绝生效
        assertThatThrownBy(() -> amendmentService.decide("AM-COV-1", "EVT-COV-1", true,
                List.of("MAX_AMOUNT"), PendingPresentationPolicy.KEEP_OLD_VERSION,
                "beneficiary-1", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PENDING_PRESENTATIONS_NOT_COVERED);
        assertThat(amendmentService.get("AM-COV-1").status())
                .isEqualTo(AmendmentStatus.PROPOSED);

        // 选择撤回重交 -> 只需覆盖累计已承兑 300，修订可以生效
        AmendmentView effective = amendmentService.decide("AM-COV-1", "EVT-COV-2", true,
                List.of("MAX_AMOUNT"), PendingPresentationPolicy.WITHDRAW_RESUBMIT,
                "beneficiary-1", null);
        assertThat(effective.status()).isEqualTo(AmendmentStatus.EFFECTIVE);
        assertThat(creditService.balance(creditNo).maxAmount()).isEqualByComparingTo("500.0000");
        assertThat(presentationService.getByNo("P-COV-2").status())
                .isEqualTo(PresentationStatus.WITHDRAWN);
    }

    @Test
    void acceptanceScopeMustMatchChangedFieldsExactly() {
        String creditNo = newCredit();
        amendmentService.propose("AM-SCP-1", creditNo, new BigDecimal("1200.0000"),
                TODAY.plusMonths(6), List.of("INVOICE", "PACKING_LIST"), "applicant-1");

        // 少确认一个字段 -> 拒绝
        assertThatThrownBy(() -> amendmentService.decide("AM-SCP-1", "EVT-SCP-1", true,
                List.of("MAX_AMOUNT"), null, "beneficiary-1", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AMENDMENT_SCOPE_MISMATCH);

        // 多确认一个不存在的字段 -> 拒绝
        assertThatThrownBy(() -> amendmentService.decide("AM-SCP-1", "EVT-SCP-2", true,
                List.of("MAX_AMOUNT", "EXPIRY_DATE", "ALLOWED_DOCUMENT_TYPES", "BOGUS"),
                null, "beneficiary-1", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AMENDMENT_SCOPE_MISMATCH);
    }

    @Test
    void acceptingWithPendingPresentationRequiresExplicitPolicy() {
        String creditNo = newCredit();
        presentationService.present("P-POL-1", creditNo, new BigDecimal("100.0000"),
                TODAY, List.of(doc("INVOICE")));
        amendmentService.propose("AM-POL-1", creditNo, new BigDecimal("1200.0000"),
                TODAY.plusMonths(6), ALLOWED, "applicant-1");

        assertThatThrownBy(() -> amendmentService.decide("AM-POL-1", "EVT-POL-1", true,
                List.of("MAX_AMOUNT", "EXPIRY_DATE"), null, "beneficiary-1", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PENDING_PRESENTATION_POLICY_REQUIRED);
    }

    @Test
    void keepOldVersionLetsPendingPresentationContinueUnderOldVersionAndCreatesNewCreditVersion() {
        String creditNo = newCredit();
        // 版本 1 只允许 INVOICE；交单在版本 1 下无差异。新版本 2 将新增要求 PACKING_LIST。
        presentationService.present("P-KEEP-1", creditNo, new BigDecimal("100.0000"),
                TODAY, List.of(doc("INVOICE")));
        amendmentService.propose("AM-KEEP-1", creditNo, new BigDecimal("1000.0000"),
                TODAY.plusMonths(6), List.of("INVOICE", "PACKING_LIST"), "applicant-1");

        AmendmentView effective = amendmentService.decide("AM-KEEP-1", "EVT-KEEP-1", true,
                List.of("EXPIRY_DATE", "ALLOWED_DOCUMENT_TYPES"),
                PendingPresentationPolicy.KEEP_OLD_VERSION, "beneficiary-1", null);

        assertThat(effective.status()).isEqualTo(AmendmentStatus.EFFECTIVE);
        CreditBalanceView balance = creditService.balance(creditNo);
        assertThat(balance.currentVersionNo()).isEqualTo(2);
        assertThat(balance.allowedDocumentTypes()).containsExactly("INVOICE", "PACKING_LIST");

        // 既有未承兑交单仍依据版本 1：不按新版本自动重审，旧审核版本仍无差异
        PresentationView presentation = presentationService.getByNo("P-KEEP-1");
        assertThat(presentation.creditVersionNo()).isEqualTo(1);
        assertThat(presentation.status()).isEqualTo(PresentationStatus.PRESENTED);
        assertThat(presentation.versions()).hasSize(1);
        assertThat(presentation.versions().get(0).clean()).isTrue();

        // 直接承兑旧交单，承兑台账记录所依据的信用证版本 1
        AcceptanceView acceptance = acceptanceService.accept("P-KEEP-1",
                new BigDecimal("100.0000"), null, null, "o1");
        assertThat(acceptance.creditVersionNo()).isEqualTo(1);

        // 新版本交单依据版本 2：只交 INVOICE 会缺少新版本要求的 PACKING_LIST
        presentationService.present("P-KEEP-2", creditNo, new BigDecimal("100.0000"),
                TODAY, List.of(doc("INVOICE")));
        PresentationView newer = presentationService.getByNo("P-KEEP-2");
        assertThat(newer.creditVersionNo()).isEqualTo(2);
        assertThat(newer.versions().get(0).clean()).isFalse();
        assertThat(newer.versions().get(0).discrepancies().get(0).getKey())
                .isEqualTo("MISSING_REQUIRED_DOCUMENT:PACKING_LIST");

        // 按新版本补齐单据后无差异
        presentationService.supplement("P-KEEP-2", List.of(doc("PACKING_LIST")));
        assertThat(presentationService.getByNo("P-KEEP-2").latestVersionNo()).isEqualTo(2);
        assertThat(presentationService.getByNo("P-KEEP-2").versions().get(1).clean()).isTrue();
    }

    @Test
    void withdrawResubmitWithdrawsPendingPresentationsAndTheyCannotBeAcceptedOrSupplemented() {
        String creditNo = newCredit();
        presentationService.present("P-WD-1", creditNo, new BigDecimal("100.0000"),
                TODAY, List.of(doc("INVOICE")));
        amendmentService.propose("AM-WD-1", creditNo, new BigDecimal("1200.0000"),
                TODAY.plusMonths(6), ALLOWED, "applicant-1");

        amendmentService.decide("AM-WD-1", "EVT-WD-1", true,
                List.of("MAX_AMOUNT", "EXPIRY_DATE"),
                PendingPresentationPolicy.WITHDRAW_RESUBMIT, "beneficiary-1", null);

        PresentationView withdrawn = presentationService.getByNo("P-WD-1");
        assertThat(withdrawn.status()).isEqualTo(PresentationStatus.WITHDRAWN);
        // 撤回不释放余额（本就未承兑），但不能再承兑或补交
        assertThatThrownBy(() -> acceptanceService.accept("P-WD-1",
                new BigDecimal("100.0000"), null, null, "o1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRESENTATION_WITHDRAWN);
        assertThatThrownBy(() -> presentationService.supplement("P-WD-1",
                List.of(doc("INVOICE"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRESENTATION_WITHDRAWN);

        // 撤回交单不占用余额：新交单可在新版本下使用全部额度
        presentationService.present("P-WD-2", creditNo, new BigDecimal("100.0000"),
                TODAY, List.of(doc("INVOICE")));
        assertThat(presentationService.getByNo("P-WD-2").creditVersionNo()).isEqualTo(2);
    }

    @Test
    void oldVersionAndExistingAcceptanceRemainImmutableAfterEffective() {
        String creditNo = newCredit();
        presentationService.present("P-IMM-1", creditNo, new BigDecimal("200.0000"),
                TODAY, List.of(doc("INVOICE")));
        AcceptanceView before = acceptanceService.accept("P-IMM-1",
                new BigDecimal("200.0000"), null, null, "officer-1");

        amendmentService.propose("AM-IMM-1", creditNo, new BigDecimal("1500.0000"),
                TODAY.plusMonths(6), List.of("INVOICE", "PACKING_LIST"), "applicant-1");
        amendmentService.decide("AM-IMM-1", "EVT-IMM-1", true,
                List.of("MAX_AMOUNT", "EXPIRY_DATE", "ALLOWED_DOCUMENT_TYPES"),
                null, "beneficiary-1", null);

        List<CreditVersionView> versions = versionService.listVersions(creditNo);
        assertThat(versions).hasSize(2);
        CreditVersionView v1 = versions.get(0);
        CreditVersionView v2 = versions.get(1);
        assertThat(v1.versionNo()).isEqualTo(1);
        assertThat(v1.maxAmount()).isEqualByComparingTo("1000.0000");
        assertThat(v1.allowedDocumentTypes()).containsExactly("INVOICE");
        assertThat(v1.frozenAcceptedAmount()).isEqualByComparingTo("0");
        assertThat(v2.versionNo()).isEqualTo(2);
        assertThat(v2.maxAmount()).isEqualByComparingTo("1500.0000");
        // 新版本以实时累计承兑冻结剩余快照：1500 − 200 = 1300
        assertThat(v2.frozenAcceptedAmount()).isEqualByComparingTo("200.0000");
        assertThat(v2.frozenAvailableAmount()).isEqualByComparingTo("1300.0000");

        // 既有承兑要素与所依据版本不变
        AcceptanceView after = acceptanceService.getByNo(before.acceptanceNo());
        assertThat(after.amount()).isEqualByComparingTo("200.0000");
        assertThat(after.creditVersionNo()).isEqualTo(1);
        assertThat(after.status()).isEqualTo(AcceptanceStatus.ACCEPTED);

        CreditVersionDiffView diff = versionService.diff(creditNo, 1, 2);
        assertThat(diff.changedFields()).containsExactly("ALLOWED_DOCUMENT_TYPES",
                "EXPIRY_DATE", "MAX_AMOUNT");
        assertThat(diff.maxAmountAfter()).isEqualByComparingTo("1500.0000");
        assertThat(diff.docTypesAfter()).containsExactly("INVOICE", "PACKING_LIST");
    }

    @Test
    void decisionEventNumberIsIdempotentAndSecondDecisionRejected() {
        String creditNo = newCredit();
        amendmentService.propose("AM-IDM-1", creditNo, new BigDecimal("1200.0000"),
                TODAY.plusMonths(6), ALLOWED, "applicant-1");

        AmendmentView first = amendmentService.decide("AM-IDM-1", "EVT-IDM-1", true,
                List.of("MAX_AMOUNT", "EXPIRY_DATE"), null, "beneficiary-1", "ok");
        AmendmentView retry = amendmentService.decide("AM-IDM-1", "EVT-IDM-1", true,
                List.of("MAX_AMOUNT", "EXPIRY_DATE"), null, "beneficiary-1", "ok");
        assertThat(retry.status()).isEqualTo(first.status());
        assertThat(creditService.balance(creditNo).currentVersionNo()).isEqualTo(2);

        // 生效后不能再登记其他决定
        assertThatThrownBy(() -> amendmentService.decide("AM-IDM-1", "EVT-IDM-9", false,
                List.of(), null, "beneficiary-1", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AMENDMENT_NOT_ACTIVE);
    }

    @Test
    void rejectionKeepsApplicationAndDoesNotChangeCreditVersion() {
        String creditNo = newCredit();
        amendmentService.propose("AM-REJ-1", creditNo, new BigDecimal("1200.0000"),
                TODAY.plusMonths(6), ALLOWED, "applicant-1");

        AmendmentView rejected = amendmentService.decide("AM-REJ-1", "EVT-REJ-1", false,
                List.of(), null, "beneficiary-1", "不同意");
        assertThat(rejected.status()).isEqualTo(AmendmentStatus.REJECTED);
        assertThat(rejected.decision().accepted()).isFalse();
        assertThat(rejected.decision().remark()).isEqualTo("不同意");

        assertThat(creditService.balance(creditNo).currentVersionNo()).isEqualTo(1);
        assertThat(versionService.listVersions(creditNo)).hasSize(1);

        // 拒绝后可重新提出修订（不再有活动修订），序号递增
        AmendmentView second = amendmentService.propose("AM-REJ-2", creditNo,
                new BigDecimal("1300.0000"), TODAY.plusMonths(6), ALLOWED, "applicant-1");
        assertThat(second.amendmentSeq()).isEqualTo(2);
        assertThat(amendmentService.listByCredit(creditNo)).hasSize(2);
    }

    @Test
    void cancellationKeepsApplicationAndIsIdempotentByCancelEventNumber() {
        String creditNo = newCredit();
        amendmentService.propose("AM-CAN-1", creditNo, new BigDecimal("1200.0000"),
                TODAY.plusMonths(6), ALLOWED, "applicant-1");

        AmendmentView cancelled = amendmentService.cancel("AM-CAN-1", "CEV-CAN-1",
                "applicant-1", "条款需要调整");
        assertThat(cancelled.status()).isEqualTo(AmendmentStatus.CANCELLED);
        assertThat(cancelled.cancellationReason()).isEqualTo("条款需要调整");
        assertThat(creditService.balance(creditNo).currentVersionNo()).isEqualTo(1);

        // 同一取消事件号重复取消 -> 幂等
        AmendmentView again = amendmentService.cancel("AM-CAN-1", "CEV-CAN-1",
                "applicant-1", "条款需要调整");
        assertThat(again.status()).isEqualTo(AmendmentStatus.CANCELLED);

        // 已取消不能再决定，也不能用别的事件号再取消
        assertThatThrownBy(() -> amendmentService.decide("AM-CAN-1", "EVT-CAN-1", true,
                List.of("MAX_AMOUNT", "EXPIRY_DATE"), null, "beneficiary-1", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AMENDMENT_NOT_ACTIVE);
        assertThatThrownBy(() -> amendmentService.cancel("AM-CAN-1", "CEV-CAN-2",
                "applicant-1", "再次取消"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AMENDMENT_NOT_ACTIVE);
    }

    @Test
    void concurrentAcceptanceAndAmendmentEffectiveNeverExceedAmountAndRechecksLiveBalance()
            throws Exception {
        String creditNo = newCredit();
        presentationService.present("P-CCX-1", creditNo, new BigDecimal("800.0000"),
                TODAY, List.of(doc("INVOICE")));
        presentationService.present("P-CCX-2", creditNo, new BigDecimal("800.0000"),
                TODAY, List.of(doc("INVOICE")));
        // 修订把最高金额降到 700，受益人选择撤回重交：
        // 承兑先到 -> 锁内重算累计已承兑 800 > 700，修订必须失败；
        // 修订先生效 -> 两笔交单撤回，承兑随后被拒（PRESENTATION_WITHDRAWN）。
        amendmentService.propose("AM-CCX-1", creditNo, new BigDecimal("700.0000"),
                TODAY.plusMonths(3), ALLOWED, "applicant-1");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<ErrorCode> acceptError = new AtomicReference<>();
        AtomicReference<ErrorCode> amendError = new AtomicReference<>();

        Future<?> acceptFuture = pool.submit(() -> {
            await(start);
            try {
                acceptanceService.accept("P-CCX-1", new BigDecimal("800.0000"),
                        null, null, "o1");
            } catch (BusinessException e) {
                acceptError.set(e.getErrorCode());
            }
            return null;
        });
        Future<?> amendFuture = pool.submit(() -> {
            await(start);
            try {
                amendmentService.decide("AM-CCX-1", "EVT-CCX-1", true,
                        List.of("MAX_AMOUNT"), PendingPresentationPolicy.WITHDRAW_RESUBMIT,
                        "beneficiary-1", null);
            } catch (BusinessException e) {
                amendError.set(e.getErrorCode());
            }
            return null;
        });
        start.countDown();
        acceptFuture.get(15, TimeUnit.SECONDS);
        amendFuture.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        CreditBalanceView balance = creditService.balance(creditNo);
        // 不变量：净承兑绝不超过当前最高金额。
        assertThat(balance.acceptedAmount()).isLessThanOrEqualTo(balance.maxAmount());

        if (balance.currentVersionNo() == 2) {
            // 修订先生效：交单撤回，承兑被拒
            assertThat(amendError.get()).isNull();
            assertThat(acceptError.get()).isEqualTo(ErrorCode.PRESENTATION_WITHDRAWN);
            assertThat(balance.maxAmount()).isEqualByComparingTo("700.0000");
            assertThat(balance.acceptedAmount()).isEqualByComparingTo("0");
            assertThat(presentationService.getByNo("P-CCX-1").status())
                    .isEqualTo(PresentationStatus.WITHDRAWN);
            assertThat(presentationService.getByNo("P-CCX-2").status())
                    .isEqualTo(PresentationStatus.WITHDRAWN);
        } else {
            // 承兑先提交：锁内实时重算发现 800 已承兑 > 新最高金额 700，修订失败
            assertThat(acceptError.get()).isNull();
            assertThat(amendError.get()).isEqualTo(ErrorCode.AMENDMENT_AMOUNT_BELOW_ACCEPTED);
            assertThat(balance.currentVersionNo()).isEqualTo(1);
            assertThat(balance.acceptedAmount()).isEqualByComparingTo("800.0000");
            assertThat(presentationService.getByNo("P-CCX-1").status())
                    .isEqualTo(PresentationStatus.ACCEPTED);
            assertThat(presentationService.getByNo("P-CCX-2").status())
                    .isEqualTo(PresentationStatus.PRESENTED);
        }
    }

    @Test
    void balanceChangesRecordAcceptanceReversalAndAmendment() {
        String creditNo = newCredit();
        presentationService.present("P-BC-1", creditNo, new BigDecimal("200.0000"),
                TODAY, List.of(doc("INVOICE")));
        String accNo = acceptanceService.accept("P-BC-1", new BigDecimal("200.0000"),
                null, null, "o1").acceptanceNo();
        acceptanceService.reverse(accNo, "m1", "撤销");
        amendmentService.propose("AM-BC-1", creditNo, new BigDecimal("1500.0000"),
                TODAY.plusMonths(6), ALLOWED, "applicant-1");
        amendmentService.decide("AM-BC-1", "EVT-BC-1", true,
                List.of("MAX_AMOUNT", "EXPIRY_DATE"), null, "beneficiary-1", null);

        List<BalanceChangeView> changes = versionService.balanceChanges(creditNo);
        assertThat(changes).hasSize(3);
        assertThat(changes.get(0).changeType()).hasToString("ACCEPTED");
        assertThat(changes.get(0).acceptedAfter()).isEqualByComparingTo("200.0000");
        assertThat(changes.get(1).changeType()).hasToString("REVERSED");
        assertThat(changes.get(1).acceptedAfter()).isEqualByComparingTo("0");
        BalanceChangeView amendmentChange = changes.get(2);
        assertThat(amendmentChange.changeType()).hasToString("AMENDMENT_EFFECTIVE");
        assertThat(amendmentChange.maxAmountBefore()).isEqualByComparingTo("1000.0000");
        assertThat(amendmentChange.maxAmountAfter()).isEqualByComparingTo("1500.0000");
        assertThat(amendmentChange.availableAfter()).isEqualByComparingTo("1500.0000");
        assertThat(amendmentChange.creditVersionNo()).isEqualTo(2);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String newCredit() {
        String creditNo = "LC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        creditService.create(creditNo, "受益人修订", "USD", new BigDecimal("1000.0000"),
                TODAY.plusMonths(3), ALLOWED);
        return creditNo;
    }

    private static DocumentSummary doc(String type) {
        return new DocumentSummary(type, 1, type + " 摘要");
    }
}
