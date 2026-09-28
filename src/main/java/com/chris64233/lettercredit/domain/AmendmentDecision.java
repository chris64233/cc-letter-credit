package com.chris64233.lettercredit.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 受益人对某笔修订的决定事件（接受 / 拒绝）。
 *
 * <p>决定一旦登记即不可修改；每笔修订至多一条决定。{@code decisionEventNo}
 * 全局唯一，作为决定事件的幂等键。决定必须针对<strong>当前修订版本</strong>：
 * 受益人声明接受的字段变化集合须与修订相对基线的实际变化集合<strong>完全一致</strong>
 * （不多不少）；当基线版本下存在尚未承兑交单时，还必须明确这些交单的归属策略。</p>
 */
@Entity
public class AmendmentDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 决定事件号，全局唯一（决定幂等键）。 */
    @Column(name = "decision_event_no", nullable = false, unique = true, length = 48)
    private String decisionEventNo;

    /** 每笔修订至多有一条受益人决定。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "amendment_id", nullable = false, unique = true)
    private Amendment amendment;

    /** true=接受（生效），false=拒绝（留痕不改版本）。 */
    @Column(name = "accepted", nullable = false)
    private boolean accepted;

    /**
     * 受益人确认接受的字段变化键集合；接受时必须与修订实际变化字段完全一致。
     * 拒绝时可为空。
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "amendment_decision_field", joinColumns = @JoinColumn(name = "decision_id"))
    @Column(name = "changed_field", nullable = false, length = 64)
    private List<String> acceptedChangedFields = new ArrayList<>();

    /**
     * 基线版本下未承兑交单的归属策略；接受且存在未承兑交单时必填。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "pending_policy", length = 32)
    private PendingPresentationPolicy pendingPresentationPolicy;

    /** 决定时基线版本下尚未承兑的交单数量（留痕）。 */
    @Column(name = "pending_presentations_at_decision", nullable = false)
    private int pendingPresentationsAtDecision;

    @Column(name = "decided_by", nullable = false, length = 64)
    private String decidedBy;

    @Column(name = "decided_at", nullable = false)
    private OffsetDateTime decidedAt;

    @Column(name = "remark", length = 1024)
    private String remark;

    protected AmendmentDecision() {
    }

    public AmendmentDecision(String decisionEventNo,
                             Amendment amendment,
                             boolean accepted,
                             List<String> acceptedChangedFields,
                             PendingPresentationPolicy pendingPresentationPolicy,
                             int pendingPresentationsAtDecision,
                             String decidedBy,
                             String remark) {
        this.decisionEventNo = decisionEventNo;
        this.amendment = amendment;
        this.accepted = accepted;
        this.acceptedChangedFields = new ArrayList<>(acceptedChangedFields);
        this.pendingPresentationPolicy = pendingPresentationPolicy;
        this.pendingPresentationsAtDecision = pendingPresentationsAtDecision;
        this.decidedBy = decidedBy;
        this.remark = remark;
        this.decidedAt = OffsetDateTime.now();
    }

    public SortedSet<String> acceptedFieldSet() {
        return new TreeSet<>(acceptedChangedFields);
    }

    public Long getId() {
        return id;
    }

    public String getDecisionEventNo() {
        return decisionEventNo;
    }

    public Amendment getAmendment() {
        return amendment;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public List<String> getAcceptedChangedFields() {
        return Collections.unmodifiableList(acceptedChangedFields);
    }

    public PendingPresentationPolicy getPendingPresentationPolicy() {
        return pendingPresentationPolicy;
    }

    public int getPendingPresentationsAtDecision() {
        return pendingPresentationsAtDecision;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public String getRemark() {
        return remark;
    }
}
