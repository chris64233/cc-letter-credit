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

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 信用证余额变动流水（只追加、不可修改）。
 *
 * <p>承兑（+已承兑占用）、撤销（−已承兑占用）与修订生效（最高金额变化导致
 * 可用金额变化，已承兑占用不变）各自登记一条，记录变动前后的最高金额、
 * 已承兑占用与变动后信用证业务版本号，支撑信用证全生命周期的余额变化查询。
 * 承兑/撤销的真实口径始终以信用证行实时余额为准，本流水仅作审计留痕。</p>
 */
@Entity
public class BalanceChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_id", nullable = false)
    private LetterCredit credit;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 32)
    private BalanceChangeType changeType;

    /** 关联业务编号：承兑编号或修订编号。 */
    @Column(name = "ref_no", nullable = false, length = 48)
    private String refNo;

    /** 变动发生后的信用证业务版本号。 */
    @Column(name = "credit_version_no", nullable = false)
    private int creditVersionNo;

    @Column(name = "max_amount_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxAmountBefore;

    @Column(name = "max_amount_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxAmountAfter;

    @Column(name = "accepted_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal acceptedBefore;

    @Column(name = "accepted_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal acceptedAfter;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    protected BalanceChange() {
    }

    public BalanceChange(LetterCredit credit,
                         BalanceChangeType changeType,
                         String refNo,
                         int creditVersionNo,
                         BigDecimal maxAmountBefore,
                         BigDecimal maxAmountAfter,
                         BigDecimal acceptedBefore,
                         BigDecimal acceptedAfter) {
        this.credit = credit;
        this.changeType = changeType;
        this.refNo = refNo;
        this.creditVersionNo = creditVersionNo;
        this.maxAmountBefore = maxAmountBefore;
        this.maxAmountAfter = maxAmountAfter;
        this.acceptedBefore = acceptedBefore;
        this.acceptedAfter = acceptedAfter;
        this.occurredAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public LetterCredit getCredit() {
        return credit;
    }

    public BalanceChangeType getChangeType() {
        return changeType;
    }

    public String getRefNo() {
        return refNo;
    }

    public int getCreditVersionNo() {
        return creditVersionNo;
    }

    public BigDecimal getMaxAmountBefore() {
        return maxAmountBefore;
    }

    public BigDecimal getMaxAmountAfter() {
        return maxAmountAfter;
    }

    public BigDecimal getAcceptedBefore() {
        return acceptedBefore;
    }

    public BigDecimal getAcceptedAfter() {
        return acceptedAfter;
    }

    public BigDecimal getAvailableBefore() {
        return maxAmountBefore.subtract(acceptedBefore);
    }

    public BigDecimal getAvailableAfter() {
        return maxAmountAfter.subtract(acceptedAfter);
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
