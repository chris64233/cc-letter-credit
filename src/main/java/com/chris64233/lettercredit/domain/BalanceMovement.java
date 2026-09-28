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
 * 信用证余额（已承兑占用金额）变动流水。
 *
 * <p>承兑（+占用）、撤销（−占用）与修订生效（0：版本切换、记录版本间余额
 * 结转）均在各自事务内追加一条不可变流水，串联后可完整回放余额变化。</p>
 */
@Entity
public class BalanceMovement {

    public enum Type {
        /** 承兑占用。 */
        ACCEPTANCE,
        /** 撤销恢复。 */
        REVERSAL,
        /** 修订生效：占用余额在版本间结转。 */
        AMENDMENT_EFFECT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_id", nullable = false)
    private LetterCredit credit;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 24)
    private Type type;

    /** 对已承兑占用金额的影响：承兑为正、撤销为负、修订结转为 0。 */
    @Column(name = "amount_delta", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountDelta;

    /** 变动后信用证已承兑占用金额。 */
    @Column(name = "accepted_amount_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal acceptedAmountAfter;

    /** 变动后信用证最高金额。 */
    @Column(name = "max_amount_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxAmountAfter;

    /** 变动后信用证版本号（业务版本，非 JPA 乐观锁）。 */
    @Column(name = "credit_version_no_after", nullable = false)
    private int creditVersionNoAfter;

    /** 关联业务单号：承兑号或修订号（流水审计）。 */
    @Column(name = "ref_no", length = 44)
    private String refNo;

    @Column(name = "operator", nullable = false, length = 64)
    private String operator;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    protected BalanceMovement() {
    }

    public BalanceMovement(LetterCredit credit,
                           Type type,
                           BigDecimal amountDelta,
                           BigDecimal acceptedAmountAfter,
                           BigDecimal maxAmountAfter,
                           int creditVersionNoAfter,
                           String refNo,
                           String operator) {
        this.credit = credit;
        this.type = type;
        this.amountDelta = amountDelta;
        this.acceptedAmountAfter = acceptedAmountAfter;
        this.maxAmountAfter = maxAmountAfter;
        this.creditVersionNoAfter = creditVersionNoAfter;
        this.refNo = refNo;
        this.operator = operator;
        this.occurredAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public LetterCredit getCredit() {
        return credit;
    }

    public Type getType() {
        return type;
    }

    public BigDecimal getAmountDelta() {
        return amountDelta;
    }

    public BigDecimal getAcceptedAmountAfter() {
        return acceptedAmountAfter;
    }

    public BigDecimal getMaxAmountAfter() {
        return maxAmountAfter;
    }

    public int getCreditVersionNoAfter() {
        return creditVersionNoAfter;
    }

    public String getRefNo() {
        return refNo;
    }

    public String getOperator() {
        return operator;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}
