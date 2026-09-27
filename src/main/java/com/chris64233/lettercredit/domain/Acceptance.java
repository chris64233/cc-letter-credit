package com.chris64233.lettercredit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 承兑台账记录。
 *
 * <p>承兑成功后不可修改；唯一允许的变更是通过独立的撤销决定
 * （完整原因 + 处理人）将状态置为 {@link AcceptanceStatus#REVERSED}，
 * 并在同一事务中恢复信用证余额。支持部分承兑：允许金额小于交单金额。</p>
 */
@Entity
public class Acceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 承兑编号，唯一（"ACC-" 前缀 + 外部交单号）。 */
    @Column(name = "acceptance_no", nullable = false, unique = true, length = 44)
    private String acceptanceNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_id", nullable = false)
    private LetterCredit credit;

    /** 每个交单至多产生一条承兑记录。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_id", nullable = false, unique = true)
    private Presentation presentation;

    /** 实际承兑所依据的审核版本。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_version_id", nullable = false)
    private ReviewVersion reviewVersion;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "accepted_by", nullable = false, length = 64)
    private String acceptedBy;

    @Column(name = "accepted_at", nullable = false)
    private OffsetDateTime acceptedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AcceptanceStatus status = AcceptanceStatus.ACCEPTED;

    /** 承兑时读取到的信用证乐观锁版本，仅作审计留痕。 */
    @Column(name = "credit_version_at_acceptance", nullable = false)
    private long creditVersionAtAcceptance;

    @Column(name = "reversed_at")
    private OffsetDateTime reversedAt;

    @Column(name = "reversed_by", length = 64)
    private String reversedBy;

    @Column(name = "reversal_reason", length = 1024)
    private String reversalReason;

    /** 乐观锁：并发撤销时第二个事务更新 0 行并回滚，杜绝余额被重复恢复。 */
    @Version
    private long version;

    protected Acceptance() {
    }

    public Acceptance(String acceptanceNo,
                      LetterCredit credit,
                      Presentation presentation,
                      ReviewVersion reviewVersion,
                      BigDecimal amount,
                      String acceptedBy,
                      long creditVersionAtAcceptance) {
        this.acceptanceNo = acceptanceNo;
        this.credit = credit;
        this.presentation = presentation;
        this.reviewVersion = reviewVersion;
        this.amount = amount;
        this.currency = presentation.getCurrency();
        this.acceptedBy = acceptedBy;
        this.acceptedAt = OffsetDateTime.now();
        this.creditVersionAtAcceptance = creditVersionAtAcceptance;
    }

    /**
     * 登记独立撤销决定。调用方必须已在同事务中恢复信用证余额。
     */
    public void reverse(String reversedBy, String reason) {
        if (status == AcceptanceStatus.REVERSED) {
            throw new IllegalStateException("承兑 " + acceptanceNo + " 已撤销");
        }
        this.status = AcceptanceStatus.REVERSED;
        this.reversedBy = reversedBy;
        this.reversalReason = reason;
        this.reversedAt = OffsetDateTime.now();
    }

    public boolean isReversed() {
        return status == AcceptanceStatus.REVERSED;
    }

    public Long getId() {
        return id;
    }

    public String getAcceptanceNo() {
        return acceptanceNo;
    }

    public LetterCredit getCredit() {
        return credit;
    }

    public Presentation getPresentation() {
        return presentation;
    }

    public ReviewVersion getReviewVersion() {
        return reviewVersion;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getAcceptedBy() {
        return acceptedBy;
    }

    public OffsetDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public AcceptanceStatus getStatus() {
        return status;
    }

    public long getCreditVersionAtAcceptance() {
        return creditVersionAtAcceptance;
    }

    public OffsetDateTime getReversedAt() {
        return reversedAt;
    }

    public String getReversedBy() {
        return reversedBy;
    }

    public String getReversalReason() {
        return reversalReason;
    }
}
