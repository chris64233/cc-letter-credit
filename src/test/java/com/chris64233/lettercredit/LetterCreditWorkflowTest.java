package com.chris64233.lettercredit;

import com.chris64233.lettercredit.domain.AcceptanceStatus;
import com.chris64233.lettercredit.domain.DocumentSummary;
import com.chris64233.lettercredit.exception.BusinessException;
import com.chris64233.lettercredit.exception.ErrorCode;
import com.chris64233.lettercredit.service.AcceptanceService;
import com.chris64233.lettercredit.service.AcceptanceView;
import com.chris64233.lettercredit.service.CreditBalanceView;
import com.chris64233.lettercredit.service.CreditService;
import com.chris64233.lettercredit.service.DiscrepancyDecisionService;
import com.chris64233.lettercredit.service.PresentationService;
import com.chris64233.lettercredit.service.PresentationView;
import com.chris64233.lettercredit.service.ReviewVersionView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 交单审核、差异处理、（部分）承兑与撤销的端到端业务规则测试。
 */
@SpringBootTest
class LetterCreditWorkflowTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);
    private static final List<String> ALLOWED = List.of("INVOICE");

    @Autowired
    private CreditService creditService;
    @Autowired
    private PresentationService presentationService;
    @Autowired
    private DiscrepancyDecisionService decisionService;
    @Autowired
    private AcceptanceService acceptanceService;

    @Test
    void cleanPresentationCanBeAcceptedDirectlyAndDeductsBalance() {
        String creditNo = newCreditNo();
        creditService.create(creditNo, "受益人甲", "USD", new BigDecimal("1000.0000"),
                TODAY.plusMonths(3), ALLOWED);
        presentationService.present("P-CLEAN-1", creditNo, new BigDecimal("300.0000"),
                TODAY, List.of(doc("INVOICE")));

        AcceptanceView acceptance = acceptanceService.accept("P-CLEAN-1",
                new BigDecimal("300.0000"), null, null, "officer-1");

        CreditBalanceView balance = creditService.balance(creditNo);
        assertThat(acceptance.status()).isEqualTo(AcceptanceStatus.ACCEPTED);
        assertThat(acceptance.amount()).isEqualByComparingTo("300.0000");
        assertThat(balance.acceptedAmount()).isEqualByComparingTo("300.0000");
        assertThat(balance.availableAmount()).isEqualByComparingTo("700.0000");
    }

    @Test
    void partialAcceptanceIsSupported() {
        String creditNo = newCreditNo();
        creditService.create(creditNo, "受益人乙", "USD", new BigDecimal("1000.0000"),
                TODAY.plusMonths(3), ALLOWED);
        presentationService.present("P-PART-1", creditNo, new BigDecimal("600.0000"),
                TODAY, List.of(doc("INVOICE")));

        AcceptanceView acceptance = acceptanceService.accept("P-PART-1",
                new BigDecimal("400.0000"), null, null, "officer-1");

        assertThat(acceptance.amount()).isEqualByComparingTo("400.0000");
        assertThat(creditService.balance(creditNo).availableAmount())
                .isEqualByComparingTo("600.0000");
    }

    @Test
    void acceptanceCannotExceedPresentationAmount() {
        String creditNo = newCreditNo();
        creditService.create(creditNo, "受益人", "USD", new BigDecimal("1000.0000"),
                TODAY.plusMonths(3), ALLOWED);
        presentationService.present("P-AMT-1", creditNo, new BigDecimal("100.0000"),
                TODAY, List.of(doc("INVOICE")));

        assertThatThrownBy(() -> acceptanceService.accept("P-AMT-1",
                new BigDecimal("101.0000"), null, null, "officer-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ACCEPTANCE_AMOUNT);
    }

    @Test
    void cumulativeAcceptanceCannotExceedCreditBalance() {
        String creditNo = newCreditNo();
        creditService.create(creditNo, "受益人丙", "USD", new BigDecimal("500.0000"),
                TODAY.plusMonths(3), ALLOWED);
        presentationService.present("P-CUM-1", creditNo, new BigDecimal("300.0000"),
                TODAY, List.of(doc("INVOICE")));
        presentationService.present("P-CUM-2", creditNo, new BigDecimal("300.0000"),
                TODAY, List.of(doc("INVOICE")));

        acceptanceService.accept("P-CUM-1", new BigDecimal("300.0000"), null, null, "o1");

        assertThatThrownBy(() -> acceptanceService.accept("P-CUM-2",
                new BigDecimal("300.0000"), null, null, "o1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AVAILABLE_BALANCE_INSUFFICIENT);
        assertThat(creditService.balance(creditNo).acceptedAmount())
                .isEqualByComparingTo("300.0000");
    }

    @Test
    void discrepantPresentationCannotBeAcceptedWithoutApplicantDecision() {
        String creditNo = newCreditNo();
        creditService.create(creditNo, "受益人丁", "USD", new BigDecimal("1000.0000"),
                TODAY.plusMonths(3), ALLOWED);
        presentationService.present("P-DISC-1", creditNo, new BigDecimal("100.0000"),
                TODAY, List.of(doc("CERTIFICATE_OF_ORIGIN")));

        assertThatThrownBy(() -> acceptanceService.accept("P-DISC-1",
                new BigDecimal("100.0000"), null, null, "officer-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DISCREPANCY_DECISION_REQUIRED);
        assertThat(creditService.balance(creditNo).acceptedAmount())
                .isEqualByComparingTo("0");
    }

    @Test
    void acceptedDiscrepancyScopeMustMatchCurrentVersionExactly() {
        String creditNo = newCreditNo();
        // 已过有效期；提交一个规定单据 + 一个清单外单据，恰好形成两条差异
        creditService.create(creditNo, "受益人戊", "USD", new BigDecimal("1000.0000"),
                TODAY.minusMonths(1), List.of("INSURANCE_POLICY"));
        presentationService.present("P-SCOPE-1", creditNo, new BigDecimal("100.0000"),
                TODAY, List.of(doc("INSURANCE_POLICY"), doc("BOGUS_DOC")));

        PresentationView view = presentationService.getByNo("P-SCOPE-1");
        assertThat(view.versions()).hasSize(1);
        assertThat(view.versions().get(0).discrepancies()).hasSize(2);

        // 少接受一个差异 -> 拒绝
        assertThatThrownBy(() -> decisionService.accept("P-SCOPE-1",
                List.of("LC_EXPIRED"), "applicant-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DISCREPANCY_SCOPE_MISMATCH);

        // 凭空多接受一个不存在的差异 -> 拒绝
        assertThatThrownBy(() -> decisionService.accept("P-SCOPE-1",
                List.of("LC_EXPIRED", "DOC_TYPE_NOT_ALLOWED:BOGUS_DOC", "AMOUNT_EXCEEDS_BALANCE"),
                "applicant-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DISCREPANCY_SCOPE_MISMATCH);

        // 完全一致 -> 接受成功，随后可承兑
        decisionService.accept("P-SCOPE-1",
                List.of("LC_EXPIRED", "DOC_TYPE_NOT_ALLOWED:BOGUS_DOC"), "applicant-1");
        AcceptanceView acceptance = acceptanceService.accept("P-SCOPE-1",
                new BigDecimal("100.0000"), null, null, "officer-1");
        assertThat(acceptance.status()).isEqualTo(AcceptanceStatus.ACCEPTED);
    }

    @Test
    void noDecisionAllowedOnCleanVersion() {
        String creditNo = newCreditNo();
        creditService.create(creditNo, "受益人", "USD", new BigDecimal("1000.0000"),
                TODAY.plusMonths(3), ALLOWED);
        presentationService.present("P-CLEAN-D", creditNo, new BigDecimal("100.0000"),
                TODAY, List.of(doc("INVOICE")));

        assertThatThrownBy(() -> decisionService.accept("P-CLEAN-D",
                List.of("LC_EXPIRED"), "applicant-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NO_DISCREPANCY_TO_ACCEPT);
    }

    @Test
    void supplementCreatesNewReviewVersionAndKeepsOldOne() {
        String creditNo = newCreditNo();
        // 信用证规定提交发票和装箱单
        creditService.create(creditNo, "受益人己", "USD", new BigDecimal("1000.0000"),
                TODAY.plusMonths(3), List.of("INVOICE", "PACKING_LIST"));
        // 首交只提交发票 -> 缺少装箱单差异
        presentationService.present("P-SUPP-1", creditNo, new BigDecimal("100.0000"),
                TODAY, List.of(doc("INVOICE")));
        decisionService.accept("P-SUPP-1",
                List.of("MISSING_REQUIRED_DOCUMENT:PACKING_LIST"), "applicant-1");

        // 补交缺失的装箱单：新版本无差异，旧版本与旧决定保留
        presentationService.supplement("P-SUPP-1", List.of(doc("PACKING_LIST")));
        PresentationView view = presentationService.getByNo("P-SUPP-1");

        assertThat(view.status()).isEqualTo(com.chris64233.lettercredit.domain.PresentationStatus.PRESENTED);
        assertThat(view.latestVersionNo()).isEqualTo(2);
        assertThat(view.versions()).hasSize(2);
        ReviewVersionView v1 = view.versions().get(0);
        ReviewVersionView v2 = view.versions().get(1);
        assertThat(v1.versionNo()).isEqualTo(1);
        assertThat(v1.clean()).isFalse();
        assertThat(v1.discrepancyAccepted()).isTrue();
        assertThat(v2.versionNo()).isEqualTo(2);
        assertThat(v2.clean()).isTrue();
        assertThat(v2.documents()).hasSize(2);
        // 旧版本差异快照未被改写
        assertThat(v1.discrepancies()).hasSize(1);

        // 新版本无差异，可直接承兑
        AcceptanceView acceptance = acceptanceService.accept("P-SUPP-1",
                new BigDecimal("100.0000"), null, null, "officer-1");
        assertThat(acceptance.reviewVersionNo()).isEqualTo(2);
    }

    @Test
    void acceptanceBasedOnStaleReviewVersionFails() {
        String creditNo = newCreditNo();
        // 规定两种单据：先缺后补，产生无差异新版本
        creditService.create(creditNo, "受益人庚", "USD", new BigDecimal("1000.0000"),
                TODAY.plusMonths(3), List.of("INVOICE", "PACKING_LIST"));
        presentationService.present("P-STALE-1", creditNo, new BigDecimal("100.0000"),
                TODAY, List.of(doc("INVOICE")));
        presentationService.supplement("P-STALE-1", List.of(doc("PACKING_LIST")));
        assertThat(presentationService.getByNo("P-STALE-1").versions().get(1).clean()).isTrue();

        // 依据旧版本 1 承兑 -> 失败，余额不动
        assertThatThrownBy(() -> acceptanceService.accept("P-STALE-1",
                new BigDecimal("100.0000"), 1, null, "officer-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REVIEW_VERSION_STALE);
        assertThat(creditService.balance(creditNo).acceptedAmount())
                .isEqualByComparingTo("0");
    }

    @Test
    void acceptanceBasedOnStaleCreditVersionFails() {
        String creditNo = newCreditNo();
        creditService.create(creditNo, "受益人辛", "USD", new BigDecimal("1000.0000"),
                TODAY.plusMonths(3), ALLOWED);
        presentationService.present("P-CV-1", creditNo, new BigDecimal("200.0000"),
                TODAY, List.of(doc("INVOICE")));
        presentationService.present("P-CV-2", creditNo, new BigDecimal("200.0000"),
                TODAY, List.of(doc("INVOICE")));

        long observedVersion = creditService.balance(creditNo).version();
        // 另一笔承兑先改变余额版本
        acceptanceService.accept("P-CV-1", new BigDecimal("200.0000"), null, null, "o1");

        assertThatThrownBy(() -> acceptanceService.accept("P-CV-2",
                new BigDecimal("200.0000"), null, observedVersion, "o2"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CREDIT_VERSION_STALE);
    }

    @Test
    void acceptanceIsIdempotentByPresentationNumber() {
        String creditNo = newCreditNo();
        creditService.create(creditNo, "受益人壬", "USD", new BigDecimal("1000.0000"),
                TODAY.plusMonths(3), ALLOWED);
        presentationService.present("P-IDEM-1", creditNo, new BigDecimal("150.0000"),
                TODAY, List.of(doc("INVOICE")));

        AcceptanceView first = acceptanceService.accept("P-IDEM-1",
                new BigDecimal("150.0000"), null, null, "o1");
        AcceptanceView second = acceptanceService.accept("P-IDEM-1",
                new BigDecimal("150.0000"), null, null, "o1");

        assertThat(second.acceptanceNo()).isEqualTo(first.acceptanceNo());
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(creditService.balance(creditNo).acceptedAmount())
                .isEqualByComparingTo("150.0000");
        assertThat(acceptanceService.ledger(creditNo, null)).hasSize(1);
    }

    @Test
    void concurrentAcceptancesNeverExceedBalance() throws Exception {
        String creditNo = newCreditNo();
        creditService.create(creditNo, "受益人并发", "USD", new BigDecimal("100.0000"),
                TODAY.plusMonths(3), ALLOWED);
        presentationService.present("P-CC-1", creditNo, new BigDecimal("80.0000"),
                TODAY, List.of(doc("INVOICE")));
        presentationService.present("P-CC-2", creditNo, new BigDecimal("80.0000"),
                TODAY, List.of(doc("INVOICE")));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<ErrorCode> loser = new AtomicReference<>();

        List<Future<?>> futures = new ArrayList<>();
        for (String no : List.of("P-CC-1", "P-CC-2")) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    acceptanceService.accept(no, new BigDecimal("80.0000"), null, null, "o1");
                } catch (BusinessException e) {
                    loser.set(e.getErrorCode());
                }
                return null;
            }));
        }
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(loser.get()).isEqualTo(ErrorCode.AVAILABLE_BALANCE_INSUFFICIENT);
        assertThat(creditService.balance(creditNo).acceptedAmount())
                .isEqualByComparingTo("80.0000");
    }

    @Test
    void concurrentAcceptanceOfSamePresentationIsIdempotent() throws Exception {
        String creditNo = newCreditNo();
        creditService.create(creditNo, "受益人同单", "USD", new BigDecimal("1000.0000"),
                TODAY.plusMonths(3), ALLOWED);
        presentationService.present("P-CSAME-1", creditNo, new BigDecimal("120.0000"),
                TODAY, List.of(doc("INVOICE")));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AcceptanceView>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return acceptanceService.accept("P-CSAME-1",
                        new BigDecimal("120.0000"), null, null, "o1");
            }));
        }
        start.countDown();
        AcceptanceView a = futures.get(0).get(10, TimeUnit.SECONDS);
        AcceptanceView b = futures.get(1).get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(a.acceptanceNo()).isEqualTo(b.acceptanceNo());
        assertThat(creditService.balance(creditNo).acceptedAmount())
                .isEqualByComparingTo("120.0000");
    }

    @Test
    void reversalIsIndependentDecisionThatRestoresBalanceWithReasonAndHandler() {
        String creditNo = newCreditNo();
        creditService.create(creditNo, "受益人撤销", "USD", new BigDecimal("500.0000"),
                TODAY.plusMonths(3), ALLOWED);
        presentationService.present("P-REV-1", creditNo, new BigDecimal("200.0000"),
                TODAY, List.of(doc("INVOICE")));
        AcceptanceView acceptance = acceptanceService.accept("P-REV-1",
                new BigDecimal("200.0000"), null, null, "officer-1");

        AcceptanceView reversed = acceptanceService.reverse(acceptance.acceptanceNo(),
                "manager-9", "受益人撤回单据，经审批撤销承兑");

        assertThat(reversed.status()).isEqualTo(AcceptanceStatus.REVERSED);
        assertThat(reversed.reversedBy()).isEqualTo("manager-9");
        assertThat(reversed.reversalReason()).isEqualTo("受益人撤回单据，经审批撤销承兑");
        assertThat(reversed.reversedAt()).isNotNull();
        // 承兑原始要素保持不变
        assertThat(reversed.amount()).isEqualByComparingTo("200.0000");
        assertThat(reversed.acceptedBy()).isEqualTo("officer-1");
        assertThat(creditService.balance(creditNo).availableAmount())
                .isEqualByComparingTo("500.0000");

        assertThatThrownBy(() -> acceptanceService.reverse(acceptance.acceptanceNo(),
                "manager-9", "再次撤销"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCEPTANCE_ALREADY_REVERSED);
    }

    @Test
    void concurrentReversalsRestoreBalanceExactlyOnce() throws Exception {
        String creditNo = newCreditNo();
        creditService.create(creditNo, "受益人并发撤销", "USD", new BigDecimal("500.0000"),
                TODAY.plusMonths(3), ALLOWED);
        presentationService.present("P-CREV-1", creditNo, new BigDecimal("200.0000"),
                TODAY, List.of(doc("INVOICE")));
        String acceptanceNo = acceptanceService.accept("P-CREV-1",
                new BigDecimal("200.0000"), null, null, "officer-1").acceptanceNo();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    acceptanceService.reverse(acceptanceNo, "manager-9", "并发撤销测试");
                } catch (BusinessException e) {
                    failures.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(failures.get()).isEqualTo(1);
        // 余额只能恢复一次
        assertThat(creditService.balance(creditNo).availableAmount())
                .isEqualByComparingTo("500.0000");
        assertThat(acceptanceService.getByNo(acceptanceNo).status())
                .isEqualTo(AcceptanceStatus.REVERSED);
    }

    @Test
    void ledgerIncludesAcceptedAndReversedRecordsAndSupportsStatusFilter() {
        String creditNo = newCreditNo();
        creditService.create(creditNo, "受益人台账", "USD", new BigDecimal("1000.0000"),
                TODAY.plusMonths(3), ALLOWED);
        presentationService.present("P-LED-1", creditNo, new BigDecimal("100.0000"),
                TODAY, List.of(doc("INVOICE")));
        presentationService.present("P-LED-2", creditNo, new BigDecimal("100.0000"),
                TODAY, List.of(doc("INVOICE")));
        String accNo1 = acceptanceService.accept("P-LED-1",
                new BigDecimal("100.0000"), null, null, "o1").acceptanceNo();
        AcceptanceView second = acceptanceService.accept("P-LED-2",
                new BigDecimal("100.0000"), null, null, "o1");
        acceptanceService.reverse(accNo1, "m1", "撤销原因");

        assertThat(acceptanceService.ledger(creditNo, null)).hasSize(2);
        assertThat(acceptanceService.ledger(creditNo, AcceptanceStatus.REVERSED))
                .extracting(AcceptanceView::acceptanceNo).containsExactly(accNo1);
        assertThat(acceptanceService.ledger(creditNo, AcceptanceStatus.ACCEPTED))
                .extracting(AcceptanceView::acceptanceNo).containsExactly(second.acceptanceNo());
    }

    @Test
    void acceptedPresentationCannotSupplementAndDuplicateNumberRejected() {
        String creditNo = newCreditNo();
        creditService.create(creditNo, "受益人终态", "USD", new BigDecimal("1000.0000"),
                TODAY.plusMonths(3), ALLOWED);
        presentationService.present("P-LOCK-1", creditNo, new BigDecimal("100.0000"),
                TODAY, List.of(doc("INVOICE")));
        acceptanceService.accept("P-LOCK-1", new BigDecimal("100.0000"), null, null, "o1");

        assertThatThrownBy(() -> presentationService.supplement("P-LOCK-1",
                List.of(doc("PACKING_LIST"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRESENTATION_ALREADY_ACCEPTED);

        assertThatThrownBy(() -> presentationService.present("P-LOCK-1", creditNo,
                new BigDecimal("100.0000"), TODAY, List.of(doc("INVOICE"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_PRESENTATION_NO);
    }

    private static String newCreditNo() {
        return "LC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static DocumentSummary doc(String type) {
        return new DocumentSummary(type, 1, type + " 摘要");
    }
}
