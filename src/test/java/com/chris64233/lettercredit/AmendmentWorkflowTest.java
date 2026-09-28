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
import com.chris64233.lettercredit.service.BalanceMovementView;
import com.chris64233.lettercredit.service.CreditBalanceView;
import com.chris64233.lettercredit.service.CreditService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 信用证修订（修改）流程的端到端业务规则与并发测试。
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

    // ---- 提出修订 ----

    @Test
    void proposeFreezesCurrentVersionAndRemainingAmount() {
        String creditNo = newCredit();
        createCredit(creditNo, "1000.0000");
        presentationService.present("P-FRZ-1", creditNo, new BigDecimal("300.0000"),
                TODAY, List.of(doc("INVOICE")));
        acceptanceService.accept("P-FRZ-1", new BigDecimal("300.0000"), null, null, "o1");

        AmendmentView a = amendmentService.propose("AMD-FRZ-1", creditNo,
                new BigDecimal("1200.0000"), TODAY.plusMonths(6), ALLOWED,
                null, "applicant-1");

        assertThat(a.status()).isEqualTo(AmendmentStatus.PROPOSED);
        assertThat(a.baseVersionNo()).isEqualTo(1);
        assertThat(a.frozenAcceptedAmount()).isEqualByComparingTo("300.0000");
        assertThat(a.frozenRemainingAmount()).isEqualByComparingTo("700.0000");
        assertThat(a.affectedFields()).containsExactly("EXPIRY_DATE", "MAX_AMOUNT");
        assertThat(a.pendingPresentationNos()).isEmpty();
        assertThat(a.pendingPolicy()).isNull();
        // 修订待决期间信用证当前版本不变
        assertThat(creditService.balance(creditNo).currentVersionNo()).isEqualTo(1);
    }

    @Test
    void duplicateAmendmentNumberIsIdempotent() {
        String creditNo = newCredit();
        createCredit(creditNo, "1000.0000");
        AmendmentView first = propose("AMD-IDEM-1", creditNo, "900.0000", null);
        AmendmentView again = propose("AMD-IDEM-1", creditNo, "900.0000", null);

        assertThat(again.amendmentNo()).isEqualTo(first.amendmentNo());
        assertThat(amendmentService.listByCredit(creditNo)).hasSize(1);

        // 同号但内容不一致 -> 冲突
        assertThatThrownBy(() -> propose("AMD-IDEM-1", creditNo, "800.0000", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AMENDMENT_DECISION_CONFLICT);
    }

    @Test
    void onlyOneActiveAmendmentPerCredit() {
        String creditNo = newCredit();
        createCredit(creditNo, "1000.0000");
        propose("AMD-ACT-1", creditNo, "900.0000", null);

        assertThatThrownBy(() -> propose("AMD-ACT-2", creditNo, "800.0000", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACTIVE_AMENDMENT_EXISTS);

        // 拒绝后可再提出新修订
        reject("AMD-ACT-1", "EVT-ACT-1", "beneficiary-1", "不同意");
        AmendmentView second = propose("AMD-ACT-3", creditNo, "800.0000", null);
        assertThat(second.status()).isEqualTo(AmendmentStatus.PROPOSED);
        // 被拒绝的申请仍然保留
        assertThat(amendmentService.listByCredit(creditNo))
                .extracting(AmendmentView::amendmentNo)
                .containsExactly("AMD-ACT-1", "AMD-ACT-3");
    }

    @Test
    void amendmentWithoutAnyChangeIsRejected() {
        String creditNo = newCredit();
        createCredit(creditNo, "1000.0000");
        assertThatThrownBy(() -> propose("AMD-NC-1", creditNo, "1000.0000", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AMENDMENT_NO_CHANGE);
    }

    @Test
    void reducingAmountBelowAcceptedTotalIsRejected() {
        String creditNo = newCredit();
        createCredit(creditNo, "500.0000");
        presentationService.present("P-LOW-1", creditNo, new BigDecimal("400.0000"),
                TODAY, List.of(doc("INVOICE")));
        acceptanceService.accept("P-LOW-1", new BigDecimal("400.0000"), null, null, "o1");

        assertThatThrownBy(() -> propose("AMD-LOW-1", creditNo, "399.0000", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AMENDMENT_AMOUNT_BELOW_ACCEPTED);

        // 恰好等于已承兑累计可以提出
        AmendmentView ok = propose("AMD-LOW-2", creditNo, "400.0000", null);
        assertThat(ok.proposedMaxAmount()).isEqualByComparingTo("400.0000");
    }

    @Test
    void keepOldPolicyReducingBelowAcceptedPlusPendingIsRejected() {
        String creditNo = newCredit();
        createCredit(creditNo, "1000.0000");
        presentationService.present("P-KEEP-1", creditNo, new BigDecimal("300.0000"),
                TODAY, List.of(doc("INVOICE")));
        presentationService.present("P-KEEP-2", creditNo, new BigDecimal("300.0000"),
                TODAY, List.of(doc("INVOICE")));
        acceptanceService.accept("P-KEEP-1", new BigDecimal("300.0000"), null, null, "o1");
        // 已承兑 300，未承兑 P-KEEP-2 金额 300；保留旧版本时新额度必须 >= 600

        // 未指定处置方式 -> 必须明确
        assertThatThrownBy(() -> propose("AMD-KEEP-1", creditNo, "500.0000", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PENDING_PRESENTATION_POLICY_REQUIRED);

        // 保留旧版本但额度 500 < 300+300 -> 拒绝
        assertThatThrownBy(() -> amendmentService.propose("AMD-KEEP-2", creditNo,
                new BigDecimal("500.0000"), TODAY.plusMonths(3), ALLOWED,
                PendingPresentationPolicy.KEEP_OLD_VERSION, "ap"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AMENDMENT_AMOUNT_BELOW_ACCEPTED);

        // 撤回补交策略下，未承兑交单不再占用额度，500 >= 已承兑 300 即可
        AmendmentView withdraw = amendmentService.propose("AMD-KEEP-3", creditNo,
                new BigDecimal("500.0000"), TODAY.plusMonths(3), ALLOWED,
                PendingPresentationPolicy.WITHDRAW_AND_RESUBMIT, "ap");
        assertThat(withdraw.pendingPresentationNos()).containsExactly("P-KEEP-2");
    }

    @Test
    void policyNotApplicableMustNotBeProvided() {
        String creditNo = newCredit();
        createCredit(creditNo, "1000.0000");
        // 无未承兑交单，却指定处置方式 -> 拒绝
        assertThatThrownBy(() -> amendmentService.propose("AMD-PNA-1", creditNo,
                new BigDecimal("900.0000"), TODAY.plusMonths(3), ALLOWED,
                PendingPresentationPolicy.KEEP_OLD_VERSION, "ap"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PENDING_PRESENTATION_POLICY_NOT_APPLICABLE);

        // 改变单据清单但无未承兑交单，同样不允许指定处置方式
        assertThatThrownBy(() -> amendmentService.propose("AMD-PNA-2", creditNo,
                new BigDecimal("1000.0000"), TODAY.plusMonths(3),
                List.of("INVOICE", "PACKING_LIST"),
                PendingPresentationPolicy.WITHDRAW_AND_RESUBMIT, "ap"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PENDING_PRESENTATION_POLICY_NOT_APPLICABLE);
    }

    @Test
    void newPresentationBlockedWhileAmendmentActive() {
        String creditNo = newCredit();
        createCredit(creditNo, "1000.0000");
        propose("AMD-BLK-1", creditNo, "900.0000", null);

        assertThatThrownBy(() -> presentationService.present("P-BLK-1", creditNo,
                new BigDecimal("100.0000"), TODAY, List.of(doc("INVOICE"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AMENDMENT_PENDING_PRESENTATION_BLOCKED);
    }

    // ---- 受益人决定与生效 ----

    @Test
    void acceptanceCreatesNewCreditVersionAndKeepsHistory() {
        String creditNo = newCredit();
        createCredit(creditNo, "1000.0000");
        amendmentService.propose("AMD-EFF-1", creditNo, new BigDecimal("1500.0000"),
                TODAY.plusMonths(6), List.of("INVOICE", "PACKING_LIST"), null, "ap");

        AmendmentView accepted = accept("AMD-EFF-1", "EVT-EFF-1", "1500.0000",
                TODAY.plusMonths(6), List.of("INVOICE", "PACKING_LIST"),
                List.of("ALLOWED_DOCUMENT_TYPES", "EXPIRY_DATE", "MAX_AMOUNT"),
                "beneficiary-1");

        assertThat(accepted.status()).isEqualTo(AmendmentStatus.ACCEPTED);
        assertThat(accepted.decision()).isNotNull();
        assertThat(accepted.decision().accepted()).isTrue();

        CreditBalanceView balance = creditService.balance(creditNo);
        assertThat(balance.currentVersionNo()).isEqualTo(2);
        assertThat(balance.maxAmount()).isEqualByComparingTo("1500.0000");
        assertThat(balance.expiryDate()).isEqualTo(TODAY.plusMonths(6));
        assertThat(balance.allowedDocumentTypes())
                .containsExactly("INVOICE", "PACKING_LIST");

        List<CreditVersionView> versions = creditService.versions(creditNo);
        assertThat(versions).hasSize(2);
        CreditVersionView v1 = versions.get(0);
        CreditVersionView v2 = versions.get(1);
        assertThat(v1.versionNo()).isEqualTo(1);
        assertThat(v1.status()).isEqualTo(
                com.chris64233.lettercredit.domain.CreditVersionStatus.SUPERSEDED);
        assertThat(v1.maxAmount()).isEqualByComparingTo("1000.0000");
        assertThat(v2.versionNo()).isEqualTo(2);
        assertThat(v2.status()).isEqualTo(
                com.chris64233.lettercredit.domain.CreditVersionStatus.CURRENT);
        assertThat(v2.sourceAmendmentNo()).isEqualTo("AMD-EFF-1");
    }

    @Test
    void decisionEventNumberIsIdempotent() {
        String creditNo = newCredit();
        createCredit(creditNo, "1000.0000");
        propose("AMD-EVI-1", creditNo, "900.0000", null);

        AmendmentView d1 = accept("AMD-EVI-1", "EVT-EVI-1", "900.0000",
                TODAY.plusMonths(3), ALLOWED, List.of("MAX_AMOUNT"), "ben-1");
        // 同事件号重复提交（即使内容不同），原样返回既有决定，不重复生效
        AmendmentView d2 = reject("AMD-EVI-1", "EVT-EVI-1", "ben-1", "重复事件");

        assertThat(d2.status()).isEqualTo(AmendmentStatus.ACCEPTED);
        assertThat(d2.decision().eventNo()).isEqualTo("EVT-EVI-1");
        assertThat(creditService.versions(creditNo)).hasSize(2);
        assertThat(d1.decision().id()).isEqualTo(d2.decision().id());
    }

    @Test
    void acceptScopeMustMatchAmendmentExactly() {
        String creditNo = newCredit();
        createCredit(creditNo, "1000.0000");
        amendmentService.propose("AMD-SCP-1", creditNo, new BigDecimal("1200.0000"),
                TODAY.plusMonths(6), ALLOWED, null, "ap");

        // 目标金额与修订不一致
        assertThatThrownBy(() -> accept("AMD-SCP-1", "EVT-SCP-1", "1300.0000",
                TODAY.plusMonths(6), ALLOWED,
                List.of("EXPIRY_DATE", "MAX_AMOUNT"), "ben-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AMENDMENT_SCOPE_MISMATCH);

        // 金额一致但受影响字段集合少了一个
        assertThatThrownBy(() -> accept("AMD-SCP-1", "EVT-SCP-2", "1200.0000",
                TODAY.plusMonths(6), ALLOWED, List.of("MAX_AMOUNT"), "ben-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AMENDMENT_SCOPE_MISMATCH);

        // 修订仍处于活动状态，信用证版本未被失败的接受尝试改变
        assertThat(amendmentService.getByNo("AMD-SCP-1").status())
                .isEqualTo(AmendmentStatus.PROPOSED);
        assertThat(creditService.balance(creditNo).currentVersionNo()).isEqualTo(1);
    }

    @Test
    void rejectAndCancelKeepCurrentVersionButRetainRecords() {
        String creditNo = newCredit();
        createCredit(creditNo, "1000.0000");
        propose("AMD-REJ-1", creditNo, "900.0000", null);
        AmendmentView rejected = reject("AMD-REJ-1", "EVT-REJ-1",
                "beneficiary-1", "受益人拒绝降额");

        assertThat(rejected.status()).isEqualTo(AmendmentStatus.REJECTED);
        assertThat(rejected.decision().reason()).isEqualTo("受益人拒绝降额");
        assertThat(creditService.balance(creditNo).currentVersionNo()).isEqualTo(1);
        assertThat(creditService.balance(creditNo).maxAmount())
                .isEqualByComparingTo("1000.0000");
        // 终态后不能再决定
        assertThatThrownBy(() -> accept("AMD-REJ-1", "EVT-REJ-2", "900.0000",
                TODAY.plusMonths(3), ALLOWED, List.of("MAX_AMOUNT"), "ben-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AMENDMENT_NOT_ACTIVE);

        // 另一笔修订走取消
        propose("AMD-CAN-1", creditNo, "800.0000", null);
        AmendmentView cancelled = amendmentService.cancel("AMD-CAN-1", "applicant-1",
                "商务条件变化，取消修改");
        assertThat(cancelled.status()).isEqualTo(AmendmentStatus.CANCELLED);
        assertThat(cancelled.cancelledBy()).isEqualTo("applicant-1");
        assertThat(creditService.balance(creditNo).currentVersionNo()).isEqualTo(1);
        assertThat(amendmentService.listByCredit(creditNo)).hasSize(2);
    }

    // ---- 修订对既有交单的影响 ----

    @Test
    void keepOldVersionPresentationContinuesUnderOldTerms() {
        String creditNo = newCredit();
        // 版本 1 只允许 INVOICE；一笔干净的 INVOICE 交单尚未承兑
        creditService.create(creditNo, "受益人保留旧版", "USD", new BigDecimal("1000.0000"),
                TODAY.plusMonths(1), List.of("INVOICE"));
        presentationService.present("P-OLD-1", creditNo, new BigDecimal("200.0000"),
                TODAY, List.of(doc("INVOICE")));

        // 修订：允许单据清单整体改为 PACKING_LIST + 延期；旧交单保留旧版本
        amendmentService.propose("AMD-OLD-1", creditNo, new BigDecimal("1000.0000"),
                TODAY.plusMonths(6), List.of("PACKING_LIST"),
                PendingPresentationPolicy.KEEP_OLD_VERSION, "ap");
        accept("AMD-OLD-1", "EVT-OLD-1", "1000.0000", TODAY.plusMonths(6),
                List.of("PACKING_LIST"),
                List.of("ALLOWED_DOCUMENT_TYPES", "EXPIRY_DATE"), "ben-1");

        PresentationView p = presentationService.getByNo("P-OLD-1");
        assertThat(p.creditVersionNo()).isEqualTo(1);
        assertThat(p.status()).isEqualTo(PresentationStatus.PRESENTED);
        // 旧交单仍按版本 1 条款（INVOICE）审核与承兑
        AcceptanceView acc = acceptanceService.accept("P-OLD-1",
                new BigDecimal("200.0000"), null, null, "o1");
        assertThat(acc.creditVersionNo()).isEqualTo(1);

        // 新交单按新版本 2：提交 PACKING_LIST 即无差异；提交 INVOICE 反而是清单外单据
        presentationService.present("P-NEW-1", creditNo, new BigDecimal("100.0000"),
                TODAY.plusMonths(3), List.of(doc("PACKING_LIST")));
        PresentationView np = presentationService.getByNo("P-NEW-1");
        assertThat(np.creditVersionNo()).isEqualTo(2);
        assertThat(np.versions().get(0).clean()).isTrue();

        presentationService.present("P-NEW-2", creditNo, new BigDecimal("50.0000"),
                TODAY.plusMonths(3), List.of(doc("INVOICE")));
        assertThat(presentationService.getByNo("P-NEW-2").versions().get(0).clean())
                .as("新版本下 INVOICE 已不在允许清单")
                .isFalse();
    }

    @Test
    void withdrawPolicyMarksPendingPresentationsWithdrawn() {
        String creditNo = newCredit();
        createCredit(creditNo, "1000.0000");
        presentationService.present("P-WD-1", creditNo, new BigDecimal("200.0000"),
                TODAY, List.of(doc("INVOICE")));
        presentationService.present("P-WD-2", creditNo, new BigDecimal("100.0000"),
                TODAY, List.of(doc("INVOICE")));

        amendmentService.propose("AMD-WD-1", creditNo, new BigDecimal("500.0000"),
                TODAY.plusMonths(6), ALLOWED,
                PendingPresentationPolicy.WITHDRAW_AND_RESUBMIT, "ap");
        accept("AMD-WD-1", "EVT-WD-1", "500.0000", TODAY.plusMonths(6), ALLOWED,
                List.of("EXPIRY_DATE", "MAX_AMOUNT"), "ben-1");

        assertThat(presentationService.getByNo("P-WD-1").status())
                .isEqualTo(PresentationStatus.WITHDRAWN);
        assertThat(presentationService.getByNo("P-WD-2").status())
                .isEqualTo(PresentationStatus.WITHDRAWN);

        // 撤回交单不能承兑/补交
        assertThatThrownBy(() -> acceptanceService.accept("P-WD-1",
                new BigDecimal("200.0000"), null, null, "o1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRESENTATION_WITHDRAWN);
        assertThatThrownBy(() -> presentationService.supplement("P-WD-1",
                List.of(doc("INVOICE"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRESENTATION_WITHDRAWN);

        // 撤回不占用额度；可按新版本重新交单（新外部交单号）
        presentationService.present("P-WD-NEW-1", creditNo, new BigDecimal("500.0000"),
                TODAY.plusMonths(3), List.of(doc("INVOICE")));
        AcceptanceView acc = acceptanceService.accept("P-WD-NEW-1",
                new BigDecimal("500.0000"), null, null, "o1");
        assertThat(acc.creditVersionNo()).isEqualTo(2);
        assertThat(creditService.balance(creditNo).acceptedAmount())
                .isEqualByComparingTo("500.0000");
    }

    @Test
    void existingAcceptancesStayImmutableAndVersionedAfterAmendment() {
        String creditNo = newCredit();
        createCredit(creditNo, "1000.0000");
        presentationService.present("P-IMM-1", creditNo, new BigDecimal("300.0000"),
                TODAY, List.of(doc("INVOICE")));
        AcceptanceView before = acceptanceService.accept("P-IMM-1",
                new BigDecimal("300.0000"), null, null, "o1");

        // 无未承兑交单，仅降额到恰好覆盖已承兑金额
        amendmentService.propose("AMD-IMM-1", creditNo, new BigDecimal("300.0000"),
                TODAY.plusMonths(3), ALLOWED, null, "ap");
        accept("AMD-IMM-1", "EVT-IMM-1", "300.0000", TODAY.plusMonths(3), ALLOWED,
                List.of("MAX_AMOUNT"), "ben-1");

        AcceptanceView after = acceptanceService.getByNo(before.acceptanceNo());
        assertThat(after.amount()).isEqualByComparingTo("300.0000");
        assertThat(after.creditVersionNo()).isEqualTo(1);
        assertThat(after.status()).isEqualTo(AcceptanceStatus.ACCEPTED);

        // 版本归属视图：v1 条款仍为 1000（不可变）且承兑 300 归属 v1；v2 上限 300
        List<CreditVersionView> versions = creditService.versions(creditNo);
        assertThat(versions.get(0).versionAcceptedAmount()).isEqualByComparingTo("300.0000");
        assertThat(versions.get(0).maxAmount()).isEqualByComparingTo("1000.0000");
        assertThat(versions.get(1).versionAcceptedAmount()).isEqualByComparingTo("0.0000");
        assertThat(versions.get(1).maxAmount()).isEqualByComparingTo("300.0000");
        // 统一金额信封：当前可用 = 300（新上限）− 300（既有承兑）= 0
        assertThat(creditService.balance(creditNo).availableAmount())
                .isEqualByComparingTo("0.0000");
    }

    @Test
    void amountReductionReValidatedAtEffectTime() {
        String creditNo = newCredit();
        createCredit(creditNo, "1000.0000");
        presentationService.present("P-RV-1", creditNo, new BigDecimal("100.0000"),
                TODAY, List.of(doc("INVOICE")));
        presentationService.present("P-RV-2", creditNo, new BigDecimal("600.0000"),
                TODAY, List.of(doc("INVOICE")));
        // 提出修订：已承兑 0，保留旧版本，未承兑合计 700 -> 额度 700 合法
        amendmentService.propose("AMD-RV-1", creditNo, new BigDecimal("700.0000"),
                TODAY.plusMonths(3), ALLOWED,
                PendingPresentationPolicy.KEEP_OLD_VERSION, "ap");

        // 待决期间先承兑一笔 600
        acceptanceService.accept("P-RV-2", new BigDecimal("600.0000"), null, null, "o1");

        // 生效时锁内重校验：下限 = 已承兑600 + 仍未承兑 P-RV-1(100) = 700，恰好通过
        AmendmentView accepted = accept("AMD-RV-1", "EVT-RV-1", "700.0000",
                TODAY.plusMonths(3), ALLOWED, List.of("MAX_AMOUNT"), "ben-1");
        assertThat(accepted.status()).isEqualTo(AmendmentStatus.ACCEPTED);
        // 保留在旧版本的 P-RV-1（100）受统一信封约束：当前可用 700−600=100，可承兑
        AcceptanceView rest = acceptanceService.accept("P-RV-1",
                new BigDecimal("100.0000"), null, null, "o1");
        assertThat(rest.creditVersionNo()).isEqualTo(1);
        // 承兑累计 700 用满当前最高金额 700
        CreditBalanceView balance = creditService.balance(creditNo);
        assertThat(balance.acceptedAmount()).isEqualByComparingTo("700.0000");
        assertThat(balance.availableAmount()).isEqualByComparingTo("0.0000");
        assertThat(creditService.versions(creditNo).get(0).versionAcceptedAmount())
                .isEqualByComparingTo("700.0000");
    }

    @Test
    void effectFailsWhenReductionFloorBrokenBeforeDecision() {
        // 场景一（保留旧版本）：提出后发生的承兑使“已承兑 + 未承兑”超过新额度
        String credit2 = newCredit();
        createCredit(credit2, "1000.0000");
        presentationService.present("P-RF2-1", credit2, new BigDecimal("400.0000"),
                TODAY, List.of(doc("INVOICE")));
        presentationService.present("P-RF2-2", credit2, new BigDecimal("400.0000"),
                TODAY, List.of(doc("INVOICE")));
        // 提出降额 700 并保留旧版本（覆盖 400+400=800？——提出时校验即拒绝，
        // 故先降到合法的 800）
        amendmentService.propose("AMD-RF2-1", credit2, new BigDecimal("800.0000"),
                TODAY.plusMonths(3), ALLOWED,
                PendingPresentationPolicy.KEEP_OLD_VERSION, "ap");
        // 待决期间两笔中承兑一笔 400；下限 = 400 + 未承兑 400 = 800，恰好生效
        acceptanceService.accept("P-RF2-1", new BigDecimal("400.0000"), null, null, "o1");
        accept("AMD-RF2-1", "EVT-RF2-1", "800.0000", TODAY.plusMonths(3), ALLOWED,
                List.of("MAX_AMOUNT"), "ben-1");

        // 场景二（撤回策略）：提出降额 600 后该交单被承兑 800 > 600，生效必须失败
        String credit3 = newCredit();
        createCredit(credit3, "1000.0000");
        presentationService.present("P-RF3-1", credit3, new BigDecimal("800.0000"),
                TODAY, List.of(doc("INVOICE")));
        amendmentService.propose("AMD-RF3-1", credit3, new BigDecimal("600.0000"),
                TODAY.plusMonths(6), ALLOWED,
                PendingPresentationPolicy.WITHDRAW_AND_RESUBMIT, "ap");
        acceptanceService.accept("P-RF3-1", new BigDecimal("800.0000"), null, null, "o1");
        assertThatThrownBy(() -> accept("AMD-RF3-1", "EVT-RF3-1", "600.0000",
                TODAY.plusMonths(6), ALLOWED,
                List.of("EXPIRY_DATE", "MAX_AMOUNT"), "ben-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AMENDMENT_AMOUNT_BELOW_ACCEPTED);
        assertThat(creditService.balance(credit3).currentVersionNo()).isEqualTo(1);
        // 失败后修订仍为活动状态，承兑余额保持 800
        assertThat(amendmentService.getByNo("AMD-RF3-1").status())
                .isEqualTo(AmendmentStatus.PROPOSED);
        assertThat(creditService.balance(credit3).acceptedAmount())
                .isEqualByComparingTo("800.0000");
    }

    // ---- 并发 ----

    @Test
    void amendmentEffectAndAcceptanceConcurrentlyNeverExceedVersionLimit() throws Exception {
        String creditNo = newCredit();
        createCredit(creditNo, "1000.0000");
        // 两笔未承兑交单各 800
        presentationService.present("P-CA-1", creditNo, new BigDecimal("800.0000"),
                TODAY, List.of(doc("INVOICE")));
        presentationService.present("P-CA-2", creditNo, new BigDecimal("800.0000"),
                TODAY, List.of(doc("INVOICE")));
        // 撤回策略的降额修订（降到 800）：生效后未及承兑的旧交单被撤回
        amendmentService.propose("AMD-CA-1", creditNo, new BigDecimal("800.0000"),
                TODAY.plusMonths(6), ALLOWED,
                PendingPresentationPolicy.WITHDRAW_AND_RESUBMIT, "ap");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        java.util.List<Future<?>> futures = new java.util.ArrayList<>();

        futures.add(pool.submit(() -> {
            ready.countDown();
            start.await();
            try {
                acceptanceService.accept("P-CA-1", new BigDecimal("800.0000"),
                        null, null, "o1");
            } catch (BusinessException ignored) {
                // 与修订生效串行后，承兑可能因交单被撤回而失败
            }
            return null;
        }));
        futures.add(pool.submit(() -> {
            ready.countDown();
            start.await();
            try {
                accept("AMD-CA-1", "EVT-CA-1", "800.0000", TODAY.plusMonths(6),
                        ALLOWED, List.of("EXPIRY_DATE", "MAX_AMOUNT"), "ben-1");
            } catch (BusinessException ignored) {
                // 与承兑串行后，生效可能因已承兑 800 恰好等于新额度而成功，
                // 或因状态变化失败，两种结果都必须自洽
            }
            return null;
        }));
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        for (Future<?> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // 无论二者谁先，各版本承兑累计不得超过该版本最高金额
        assertVersionCaps(creditNo);
        // P-CA-2 从未参与承兑竞争：若修订生效则它必被撤回；若未生效则仍 PRESENTED
        PresentationStatus p2 = presentationService.getByNo("P-CA-2").status();
        if (creditService.balance(creditNo).currentVersionNo() == 2) {
            assertThat(p2).isEqualTo(PresentationStatus.WITHDRAWN);
        } else {
            assertThat(p2).isEqualTo(PresentationStatus.PRESENTED);
        }
    }

    @Test
    void amendmentEffectAndReversalConcurrentlyAreSerialized() throws Exception {
        String creditNo = newCredit();
        createCredit(creditNo, "1000.0000");
        presentationService.present("P-CR-1", creditNo, new BigDecimal("300.0000"),
                TODAY, List.of(doc("INVOICE")));
        String accNo = acceptanceService.accept("P-CR-1", new BigDecimal("300.0000"),
                null, null, "o1").acceptanceNo();
        amendmentService.propose("AMD-CR-1", creditNo, new BigDecimal("300.0000"),
                TODAY.plusMonths(3), ALLOWED, null, "ap");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        java.util.List<Future<?>> futures = new java.util.ArrayList<>();
        futures.add(pool.submit(() -> {
            start.await();
            try {
                acceptanceService.reverse(accNo, "m1", "并发撤销");
            } catch (BusinessException ignored) {
                // 串行后二者均可能成功，撤销只允许一次（此处仅一笔撤销事务）
            }
            return null;
        }));
        futures.add(pool.submit(() -> {
            start.await();
            try {
                accept("AMD-CR-1", "EVT-CR-1", "300.0000", TODAY.plusMonths(3),
                        ALLOWED, List.of("MAX_AMOUNT"), "ben-1");
            } catch (BusinessException ignored) {
                // 串行后自洽即可
            }
            return null;
        }));
        start.countDown();
        for (Future<?> f : futures) {
            f.get(15, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // 余额与版本上限自洽；流水连续且金额衔接
        assertVersionCaps(creditNo);
        AcceptanceView acc = acceptanceService.getByNo(accNo);
        if (acc.status() == AcceptanceStatus.REVERSED) {
            assertThat(creditService.balance(creditNo).acceptedAmount())
                    .isEqualByComparingTo("0.0000");
        }
        List<BalanceMovementView> moves = creditService.movements(creditNo);
        assertThat(moves).size().isGreaterThanOrEqualTo(2);
        BigDecimal lastAccepted = null;
        for (BalanceMovementView m : moves) {
            if (lastAccepted != null) {
                assertThat(m.acceptedAmountAfter().subtract(lastAccepted))
                        .isEqualByComparingTo(m.amountDelta());
            }
            lastAccepted = m.acceptedAmountAfter();
        }
    }

    // ---- 查询 ----

    @Test
    void queriesExposeVersionDiffDecisionsAttributionAndMovements() {
        String creditNo = newCredit();
        creditService.create(creditNo, "受益人查询", "USD", new BigDecimal("1000.0000"),
                TODAY.plusMonths(1), List.of("INVOICE"));
        presentationService.present("P-Q-1", creditNo, new BigDecimal("400.0000"),
                TODAY, List.of(doc("INVOICE")));
        acceptanceService.accept("P-Q-1", new BigDecimal("400.0000"), null, null, "o1");
        presentationService.present("P-Q-2", creditNo, new BigDecimal("100.0000"),
                TODAY, List.of(doc("INVOICE")));

        amendmentService.propose("AMD-Q-1", creditNo, new BigDecimal("1200.0000"),
                TODAY.plusMonths(6), List.of("INVOICE", "PACKING_LIST"),
                PendingPresentationPolicy.KEEP_OLD_VERSION, "ap");
        accept("AMD-Q-1", "EVT-Q-1", "1200.0000", TODAY.plusMonths(6),
                List.of("INVOICE", "PACKING_LIST"),
                List.of("ALLOWED_DOCUMENT_TYPES", "EXPIRY_DATE", "MAX_AMOUNT"), "ben-1");
        acceptanceService.accept("P-Q-2", new BigDecimal("100.0000"), null, null, "o1");

        // 版本差异查询
        List<CreditVersionView> versions = creditService.versions(creditNo);
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).versionNo()).isEqualTo(1);
        assertThat(versions.get(0).status()).isEqualTo(
                com.chris64233.lettercredit.domain.CreditVersionStatus.SUPERSEDED);
        assertThat(versions.get(0).maxAmount()).isEqualByComparingTo("1000.0000");
        assertThat(versions.get(0).expiryDate()).isEqualTo(TODAY.plusMonths(1));
        assertThat(versions.get(0).allowedDocumentTypes()).containsExactly("INVOICE");
        assertThat(versions.get(1).maxAmount()).isEqualByComparingTo("1200.0000");
        assertThat(versions.get(1).expiryDate()).isEqualTo(TODAY.plusMonths(6));
        assertThat(versions.get(1).allowedDocumentTypes())
                .containsExactly("INVOICE", "PACKING_LIST");

        // 各交单所依据版本（两笔均交单于版本 1，保留旧版本，承兑也归属 v1）
        assertThat(presentationService.getByNo("P-Q-1").creditVersionNo()).isEqualTo(1);
        assertThat(presentationService.getByNo("P-Q-2").creditVersionNo()).isEqualTo(1);
        assertThat(acceptanceService.getByPresentationNo("P-Q-1").creditVersionNo())
                .isEqualTo(1);
        assertThat(acceptanceService.getByPresentationNo("P-Q-2").creditVersionNo())
                .isEqualTo(1);

        // 修订决定查询
        AmendmentView a = amendmentService.getByNo("AMD-Q-1");
        assertThat(a.status()).isEqualTo(AmendmentStatus.ACCEPTED);
        assertThat(a.decision().eventNo()).isEqualTo("EVT-Q-1");
        assertThat(a.decision().acceptedFields())
                .containsExactly("ALLOWED_DOCUMENT_TYPES", "EXPIRY_DATE", "MAX_AMOUNT");

        // 余额变化流水：承兑400 -> 修订结转(0) -> 承兑100，金额衔接
        List<BalanceMovementView> moves = creditService.movements(creditNo);
        assertThat(moves).extracting(BalanceMovementView::type)
                .containsExactly("ACCEPTANCE", "AMENDMENT_EFFECT", "ACCEPTANCE");
        assertThat(moves.get(0).acceptedAmountAfter()).isEqualByComparingTo("400.0000");
        assertThat(moves.get(1).creditVersionNoAfter()).isEqualTo(2);
        assertThat(moves.get(1).amountDelta()).isEqualByComparingTo("0.0000");
        assertThat(moves.get(2).acceptedAmountAfter()).isEqualByComparingTo("500.0000");
    }

    // ---- helpers ----

    private void assertVersionCaps(String creditNo) {
        CreditBalanceView balance = creditService.balance(creditNo);
        assertThat(balance.acceptedAmount())
                .as("承兑累计不得超过当前最高金额")
                .isLessThanOrEqualTo(balance.maxAmount());
        assertThat(balance.availableAmount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    private void createCredit(String creditNo, String maxAmount) {
        creditService.create(creditNo, "受益人", "USD", new BigDecimal(maxAmount),
                TODAY.plusMonths(3), ALLOWED);
    }

    /** 仅改最高金额（有效期/单据不变）的修订便捷方法。 */
    private AmendmentView propose(String amendmentNo, String creditNo, String maxAmount,
                                  PendingPresentationPolicy policy) {
        return amendmentService.propose(amendmentNo, creditNo, new BigDecimal(maxAmount),
                TODAY.plusMonths(3), ALLOWED, policy, "applicant-1");
    }

    private AmendmentView accept(String amendmentNo, String eventNo, String maxAmount,
                                 LocalDate expiryDate, List<String> docTypes,
                                 List<String> fields, String decidedBy) {
        return amendmentService.decide(amendmentNo, eventNo, true,
                new BigDecimal(maxAmount), expiryDate, docTypes, fields, decidedBy, null);
    }

    private AmendmentView reject(String amendmentNo, String eventNo, String decidedBy,
                                 String reason) {
        return amendmentService.decide(amendmentNo, eventNo, false, null, null, null,
                null, decidedBy, reason);
    }

    private static String newCredit() {
        return "LC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static DocumentSummary doc(String type) {
        return new DocumentSummary(type, 1, type + " 摘要");
    }
}
