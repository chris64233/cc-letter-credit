package com.chris64233.lettercredit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 撤销决定：针对一笔承兑的独立冲正记录，恢复信用证余额，
 * 保留完整原因和处理人。一笔承兑至多撤销一次。
 */
@Entity
@Table(name = "cancellation")
public class Cancellation {

    @Id
    @GeneratedValue
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "acceptance_id", nullable = false, unique = true)
    private Acceptance acceptance;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(nullable = false)
    private String operator;

    @Column(nullable = false)
    private Instant cancelledAt;

    protected Cancellation() {
    }

    public Cancellation(Acceptance acceptance, String reason, String operator, Instant cancelledAt) {
        this.acceptance = acceptance;
        this.reason = reason;
        this.operator = operator;
        this.cancelledAt = cancelledAt;
    }

    public Long getId() {
        return id;
    }

    public Acceptance getAcceptance() {
        return acceptance;
    }

    public String getReason() {
        return reason;
    }

    public String getOperator() {
        return operator;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
