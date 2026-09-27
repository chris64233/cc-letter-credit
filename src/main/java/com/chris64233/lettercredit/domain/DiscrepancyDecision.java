package com.chris64233.lettercredit.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 申请人对差异的接受决定。接受的差异范围必须与对应审核版本的
 * 差异清单完全一致，决定一旦作出不可修改。
 */
@Entity
@Table(name = "discrepancy_decision")
public class DiscrepancyDecision {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_id", nullable = false)
    private Presentation presentation;

    @Column(nullable = false)
    private int versionNo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "discrepancy_decision_item",
            joinColumns = @JoinColumn(name = "discrepancy_decision_id"))
    @Column(name = "discrepancy", nullable = false)
    @OrderColumn(name = "seq")
    private List<String> acceptedDiscrepancies = new ArrayList<>();

    @Column(nullable = false)
    private String decidedBy;

    @Column(nullable = false)
    private Instant decidedAt;

    protected DiscrepancyDecision() {
    }

    public DiscrepancyDecision(Presentation presentation, int versionNo,
                               List<String> acceptedDiscrepancies,
                               String decidedBy, Instant decidedAt) {
        this.presentation = presentation;
        this.versionNo = versionNo;
        this.acceptedDiscrepancies = new ArrayList<>(acceptedDiscrepancies);
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
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

    public List<String> getAcceptedDiscrepancies() {
        return List.copyOf(acceptedDiscrepancies);
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
