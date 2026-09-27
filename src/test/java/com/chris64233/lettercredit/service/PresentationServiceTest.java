package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.Acceptance;
import com.chris64233.lettercredit.domain.Cancellation;
import com.chris64233.lettercredit.domain.DocumentSummary;
import com.chris64233.lettercredit.domain.LetterCredit;
import com.chris64233.lettercredit.domain.Presentation;
import com.chris64233.lettercredit.domain.PresentationStatus;
import com.chris64233.lettercredit.domain.PresentationVersion;
import com.chris64233.lettercredit.domain.ReviewResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PresentationServiceTest {

    @Autowired
    private LetterCreditService letterCreditService;

    @Autowired
    private PresentationService presentationService;

    private LetterCredit newLetterCredit(String maxAmount) {
        return letterCreditService.createLetterCredit(
                "LC-" + UUID.randomUUID(), "受益人A", "USD",
                new BigDecimal(maxAmount), LocalDate.now().plusMonths(6),
                Set.of("INVOICE", "BOL", "PACKING_LIST"));
    }

    private static List<DocumentSummary> cleanDocs() {
        return List.of(
                new DocumentSummary("INVOICE", "INV-1", "商业发票"),
                new DocumentSummary("BOL", "BOL-1", "提单"));
    }

    @Test
    void cleanPresentationCanBeAcceptedDirectlyAndDebitsBalance() {
        LetterCredit lc = newLetterCredit("1000.00");
        Presentation p = presentationService.submitPresentation(
                lc.getId(), "EXT-1", new BigDecimal("400.00"), cleanDocs(), "审核员甲");

        PresentationVersion v1 = presentationService.listVersions(p.getId()).getFirst();
        assertThat(v1.getResult()).isEqualTo(ReviewResult.CLEAN);
        assertThat(v1.getDiscrepancies()).isEmpty();

        Acceptance acceptance = presentationService.acceptPresentation(p.getId(), 1, "承兑操作员");
        assertThat(acceptance.getAmount()).isEqualByComparingTo("400.00");
        assertThat(acceptance.getVersionNo()).isEqualTo(1);

        LetterCredit after = letterCreditService.getLetterCredit(lc.getId());
        assertThat(after.getAvailableAmount()).isEqualByComparingTo("600.00");
        assertThat(presentationService.getPresentation(p.getId()).getStatus())
                .isEqualTo(PresentationStatus.ACCEPTED);

        var ledger = presentationService.listAcceptanceLedger(lc.getId());
        assertThat(ledger).hasSize(1);
        assertThat(ledger.getFirst().isCancelled()).isFalse();
        assertThat(ledger.getFirst().acceptance().getOperator()).isEqualTo("承兑操作员");
    }

    @Test
    void discrepantPresentationRequiresExactApplicantDecision() {
        LetterCredit lc = newLetterCredit("1000.00");
        List<DocumentSummary> docs = List.of(
                new DocumentSummary("INVOICE", "INV-1", null),
                new DocumentSummary("CERT_OF_ORIGIN", "COO-1", "未允许的单据类型"));
        Presentation p = presentationService.submitPresentation(
                lc.getId(), "EXT-2", new BigDecimal("100.00"), docs, "审核员乙");

        PresentationVersion v1 = presentationService.listVersions(p.getId()).getFirst();
        assertThat(v1.getResult()).isEqualTo(ReviewResult.DISCREPANT);
        assertThat(v1.getDiscrepancies()).containsExactly("DOCUMENT_TYPE_NOT_ALLOWED:CERT_OF_ORIGIN");

        // 无差异决定时不能承兑
        assertThatThrownBy(() -> presentationService.acceptPresentation(p.getId(), 1, "op"))
                .isInstanceOf(ConflictException.class);

        // 接受范围与当前差异版本不一致（缺少部分差异）时被拒绝
        assertThatThrownBy(() -> presentationService.decideDiscrepancies(
                p.getId(), 1, List.of("SOMETHING_ELSE"), "申请人"))
                .isInstanceOf(ConflictException.class);

        // 完全一致的决定被接受
        var decision = presentationService.decideDiscrepancies(
                p.getId(), 1, List.of("DOCUMENT_TYPE_NOT_ALLOWED:CERT_OF_ORIGIN"), "申请人");
        assertThat(decision.getAcceptedDiscrepancies())
                .containsExactly("DOCUMENT_TYPE_NOT_ALLOWED:CERT_OF_ORIGIN");

        Acceptance acceptance = presentationService.acceptPresentation(p.getId(), 1, "op");
        assertThat(acceptance.getId()).isNotNull();
        assertThat(letterCreditService.getLetterCredit(lc.getId()).getAvailableAmount())
                .isEqualByComparingTo("900.00");
    }

    @Test
    void supplementCreatesNewVersionKeepsOldAndStaleAcceptanceFails() {
        LetterCredit lc = newLetterCredit("1000.00");
        Presentation p = presentationService.submitPresentation(
                lc.getId(), "EXT-3", new BigDecimal("100.00"),
                List.of(new DocumentSummary("UNKNOWN_TYPE", "X-1", null)), "审核员");

        // 补交合规单据，产生第二版审核
        PresentationVersion v2 = presentationService.supplementPresentation(
                p.getId(), cleanDocs(), "审核员");
        assertThat(v2.getVersionNo()).isEqualTo(2);
        assertThat(v2.getResult()).isEqualTo(ReviewResult.CLEAN);

        // 旧版本保留且差异清单未被修改
        List<PresentationVersion> versions = presentationService.listVersions(p.getId());
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).getDiscrepancies())
                .containsExactly("DOCUMENT_TYPE_NOT_ALLOWED:UNKNOWN_TYPE");
        assertThat(versions.get(0).getResult()).isEqualTo(ReviewResult.DISCREPANT);

        // 基于旧审核版本的承兑失败
        assertThatThrownBy(() -> presentationService.acceptPresentation(p.getId(), 1, "op"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("旧版本");

        // 基于当前版本承兑成功
        presentationService.acceptPresentation(p.getId(), 2, "op");
        assertThat(letterCreditService.getLetterCredit(lc.getId()).getAvailableAmount())
                .isEqualByComparingTo("900.00");
    }

    @Test
    void submitIsIdempotentByExternalPresentationNo() {
        LetterCredit lc = newLetterCredit("1000.00");
        Presentation first = presentationService.submitPresentation(
                lc.getId(), "EXT-4", new BigDecimal("100.00"), cleanDocs(), "审核员");
        Presentation second = presentationService.submitPresentation(
                lc.getId(), "EXT-4", new BigDecimal("100.00"), cleanDocs(), "审核员");
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(presentationService.listVersions(first.getId())).hasSize(1);

        // 相同交单号但金额不同视为冲突
        assertThatThrownBy(() -> presentationService.submitPresentation(
                lc.getId(), "EXT-4", new BigDecimal("200.00"), cleanDocs(), "审核员"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void acceptIsIdempotentForSamePresentationAndVersion() {
        LetterCredit lc = newLetterCredit("1000.00");
        Presentation p = presentationService.submitPresentation(
                lc.getId(), "EXT-5", new BigDecimal("300.00"), cleanDocs(), "审核员");

        Acceptance a1 = presentationService.acceptPresentation(p.getId(), 1, "op");
        Acceptance a2 = presentationService.acceptPresentation(p.getId(), 1, "op");
        assertThat(a2.getId()).isEqualTo(a1.getId());
        // 余额只扣减一次
        assertThat(letterCreditService.getLetterCredit(lc.getId()).getAvailableAmount())
                .isEqualByComparingTo("700.00");
        assertThat(presentationService.listAcceptanceLedger(lc.getId())).hasSize(1);
    }

    @Test
    void acceptedPresentationCannotBeSupplementedOrDecided() {
        LetterCredit lc = newLetterCredit("1000.00");
        Presentation p = presentationService.submitPresentation(
                lc.getId(), "EXT-6", new BigDecimal("100.00"), cleanDocs(), "审核员");
        presentationService.acceptPresentation(p.getId(), 1, "op");

        assertThatThrownBy(() -> presentationService.supplementPresentation(p.getId(), cleanDocs(), "审核员"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cancelAcceptanceRestoresBalanceAndKeepsReasonAndOperator() {
        LetterCredit lc = newLetterCredit("1000.00");
        Presentation p = presentationService.submitPresentation(
                lc.getId(), "EXT-7", new BigDecimal("400.00"), cleanDocs(), "审核员");
        Acceptance acceptance = presentationService.acceptPresentation(p.getId(), 1, "承兑操作员");
        assertThat(letterCreditService.getLetterCredit(lc.getId()).getAvailableAmount())
                .isEqualByComparingTo("600.00");

        Cancellation cancellation = presentationService.cancelAcceptance(
                acceptance.getId(), "受益人交单撤回，双方协商一致", "撤销审批人");
        assertThat(cancellation.getReason()).isEqualTo("受益人交单撤回，双方协商一致");
        assertThat(cancellation.getOperator()).isEqualTo("撤销审批人");

        // 余额恢复，台账保留承兑与撤销记录，承兑记录本身未被修改
        assertThat(letterCreditService.getLetterCredit(lc.getId()).getAvailableAmount())
                .isEqualByComparingTo("1000.00");
        var ledger = presentationService.listAcceptanceLedger(lc.getId());
        assertThat(ledger).hasSize(1);
        assertThat(ledger.getFirst().isCancelled()).isTrue();
        assertThat(ledger.getFirst().acceptance().getAmount()).isEqualByComparingTo("400.00");
        assertThat(ledger.getFirst().acceptance().getOperator()).isEqualTo("承兑操作员");
        assertThat(ledger.getFirst().cancellation().getOperator()).isEqualTo("撤销审批人");

        // 重复撤销失败
        assertThatThrownBy(() -> presentationService.cancelAcceptance(
                acceptance.getId(), "再次撤销", "某人"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void amountExceedingAvailableAndExpiredLcAreFlaggedAsDiscrepancies() {
        LetterCredit expired = letterCreditService.createLetterCredit(
                "LC-" + UUID.randomUUID(), "受益人B", "EUR",
                new BigDecimal("500.00"), LocalDate.now().minusDays(1), Set.of("INVOICE"));
        Presentation p = presentationService.submitPresentation(
                expired.getId(), "EXT-8", new BigDecimal("600.00"),
                List.of(new DocumentSummary("INVOICE", "INV-9", null)), "审核员");
        PresentationVersion v1 = presentationService.listVersions(p.getId()).getFirst();
        assertThat(v1.getDiscrepancies()).contains("LC_EXPIRED", "AMOUNT_EXCEEDS_AVAILABLE");
    }

    @Test
    void concurrentAcceptancesNeverOverdraw() throws Exception {
        LetterCredit lc = newLetterCredit("100.00");
        Presentation p1 = presentationService.submitPresentation(
                lc.getId(), "EXT-C1", new BigDecimal("80.00"), cleanDocs(), "审核员");
        Presentation p2 = presentationService.submitPresentation(
                lc.getId(), "EXT-C2", new BigDecimal("80.00"), cleanDocs(), "审核员");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Runnable accept1 = () -> acceptQuietly(p1.getId(), ready, go);
        Runnable accept2 = () -> acceptQuietly(p2.getId(), ready, go);
        Future<?> f1 = pool.submit(accept1);
        Future<?> f2 = pool.submit(accept2);
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        f1.get(30, TimeUnit.SECONDS);
        f2.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        // 两笔 80 的承兑只有一笔成功，余额不被超额扣减
        LetterCredit after = letterCreditService.getLetterCredit(lc.getId());
        assertThat(after.getAvailableAmount()).isEqualByComparingTo("20.00");
        assertThat(presentationService.listAcceptanceLedger(lc.getId())).hasSize(1);
    }

    private void acceptQuietly(long presentationId, CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try {
            go.await(10, TimeUnit.SECONDS);
            presentationService.acceptPresentation(presentationId, 1, "并发操作员");
        } catch (ConflictException expectedForOneThread) {
            // 另一线程承兑成功后，本线程因余额不足或乐观锁冲突失败
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
