package com.chris64233.lettercredit;

import com.chris64233.lettercredit.domain.Discrepancy;
import com.chris64233.lettercredit.domain.DiscrepancyCode;
import com.chris64233.lettercredit.domain.DocumentSummary;
import com.chris64233.lettercredit.domain.LetterCredit;
import com.chris64233.lettercredit.service.ReviewEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审核引擎规则的纯单元测试（不启动 Spring）。
 */
class ReviewEngineTest {

    private final ReviewEngine engine = new ReviewEngine();

    @Test
    void cleanPresentationHasNoDiscrepancy() {
        LetterCredit credit = credit("LC-ENG-1", new BigDecimal("1000.00"),
                LocalDate.of(2026, 12, 31), List.of("INVOICE", "PACKING_LIST"));

        List<Discrepancy> result = engine.review(credit, new BigDecimal("500.00"),
                LocalDate.of(2026, 9, 27),
                List.of(doc("INVOICE"), doc("PACKING_LIST")));

        assertThat(result).isEmpty();
    }

    @Test
    void flagsEachDisallowedDocumentTypeOnce() {
        LetterCredit credit = credit("LC-ENG-2", new BigDecimal("1000.00"),
                LocalDate.of(2026, 12, 31), List.of("INVOICE"));

        List<Discrepancy> result = engine.review(credit, new BigDecimal("100.00"),
                LocalDate.of(2026, 9, 27),
                List.of(doc("INVOICE"), doc("BILL_OF_LADING"), doc("BILL_OF_LADING")));

        assertThat(result).hasSize(1);
        Discrepancy discrepancy = result.get(0);
        assertThat(discrepancy.getCode()).isEqualTo(DiscrepancyCode.DOC_TYPE_NOT_ALLOWED);
        assertThat(discrepancy.getDocumentType()).isEqualTo("BILL_OF_LADING");
        assertThat(discrepancy.getKey()).isEqualTo("DOC_TYPE_NOT_ALLOWED:BILL_OF_LADING");
    }

    @Test
    void flagsMissingRequiredDocumentWhichSupplementCanResolve() {
        LetterCredit credit = credit("LC-ENG-4", new BigDecimal("1000.00"),
                LocalDate.of(2026, 12, 31), List.of("INVOICE", "PACKING_LIST"));

        List<Discrepancy> firstReview = engine.review(credit, new BigDecimal("100.00"),
                LocalDate.of(2026, 9, 27), List.of(doc("INVOICE")));
        assertThat(firstReview).hasSize(1);
        assertThat(firstReview.get(0).getCode()).isEqualTo(DiscrepancyCode.MISSING_REQUIRED_DOCUMENT);
        assertThat(firstReview.get(0).getDocumentType()).isEqualTo("PACKING_LIST");

        List<Discrepancy> reReview = engine.review(credit, new BigDecimal("100.00"),
                LocalDate.of(2026, 9, 27), List.of(doc("INVOICE"), doc("PACKING_LIST")));
        assertThat(reReview).isEmpty();
    }

    @Test
    void flagsExpiryAndOverBalanceTogether() {        LetterCredit credit = credit("LC-ENG-3", new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1), List.of("INVOICE"));

        List<Discrepancy> result = engine.review(credit, new BigDecimal("200.00"),
                LocalDate.of(2026, 9, 27), List.of(doc("INVOICE")));

        assertThat(result).extracting(Discrepancy::getCode)
                .containsExactly(DiscrepancyCode.LC_EXPIRED, DiscrepancyCode.AMOUNT_EXCEEDS_BALANCE);
        assertThat(result.get(0).getDocumentType()).isNull();
    }

    private static LetterCredit credit(String no, BigDecimal maxAmount, LocalDate expiry,
                                       List<String> allowedTypes) {
        LetterCredit credit = new LetterCredit(no, "受益人", "USD");
        credit.attachInitialVersion(maxAmount, expiry, allowedTypes);
        return credit;
    }

    private static DocumentSummary doc(String type) {
        return new DocumentSummary(type, 1, type + " 摘要");
    }
}
