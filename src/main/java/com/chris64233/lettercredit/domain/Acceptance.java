package com.chris64233.lettercredit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 承兑记录：一次交单至多一条，创建后不得修改；
 * 如需冲正只能通过独立的撤销决定恢复余额。
 */
@Entity
@Table(name = "acceptance")
public class Acceptance {

    @Id
    @GeneratedValue
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_id", nullable = false, unique = true)
    private Presentation presentation;

    @Column(nullable = false)
    private int versionNo;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String operator;

    @Column(nullable = false)
    private Instant acceptedAt;

    protected Acceptance() {
    }

    public Acceptance(Presentation presentation, int versionNo, BigDecimal amount,
                      String operator, Instant acceptedAt) {
        this.presentation = presentation;
        this.versionNo = versionNo;
        this.amount = amount;
        this.operator = operator;
        this.acceptedAt = acceptedAt;
    }

    public Long getId() {
        return id;
    }

    public Presentation getPresentation() {
        return presentation;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getOperator() {
        return operator;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }
}
