package com.chris64233.lettercredit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 一次交单：外部交单号在同一信用证下唯一（幂等键），
 * 每次补交单据产生新的审核版本，currentVersionNo 指向当前版本。
 */
@Entity
@Table(name = "presentation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_presentation_lc_external_no",
                columnNames = {"letter_credit_id", "externalPresentationNo"}))
public class Presentation {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "letter_credit_id", nullable = false)
    private LetterCredit letterCredit;

    @Column(nullable = false)
    private String externalPresentationNo;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PresentationStatus status = PresentationStatus.UNDER_REVIEW;

    @Column(nullable = false)
    private int currentVersionNo;

    @Column(nullable = false)
    private Instant createdAt;

    protected Presentation() {
    }

    public Presentation(LetterCredit letterCredit, String externalPresentationNo,
                        BigDecimal amount, Instant createdAt) {
        this.letterCredit = letterCredit;
        this.externalPresentationNo = externalPresentationNo;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public LetterCredit getLetterCredit() {
        return letterCredit;
    }

    public String getExternalPresentationNo() {
        return externalPresentationNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PresentationStatus getStatus() {
        return status;
    }

    public int getCurrentVersionNo() {
        return currentVersionNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void advanceVersion(int versionNo) {
        this.currentVersionNo = versionNo;
    }

    public void markAccepted() {
        this.status = PresentationStatus.ACCEPTED;
    }
}
