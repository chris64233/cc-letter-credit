package com.chris64233.lettercredit.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 受益人对某笔信用证修订的决定（接受或拒绝）。
 *
 * <p>决定事件号全局唯一、作为决定登记的<strong>幂等键</strong>；同一事件号
 * 重复提交必须返回同一决定。接受时接受范围必须与当前修订版本申请的
 * 三项内容（最高金额、有效期、允许单据类型）<strong>完全一致</strong>，
 * 不多不少。决定一旦建立不可修改。</p>
 */
@Entity
public class AmendmentDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 决定事件号，全局唯一（幂等键）。 */
    @Column(name = "event_no", nullable = false, unique = true, length = 40)
    private String eventNo;

    /** 每笔修订至多有一个受益人决定。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "amendment_id", nullable = false, unique = true)
    private CreditAmendment amendment;

    /** true=接受（生效），false=拒绝（版本不变）。 */
    @Column(name = "accepted", nullable = false)
    private boolean accepted;

    /** 接受决定回传的修订目标内容，用于审计与范围比对。 */
    @Column(name = "target_max_amount", precision = 19, scale = 4)
    private BigDecimal targetMaxAmount;

    @Column(name = "target_expiry_date")
    private LocalDate targetExpiryDate;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "amend_decision_doc_type",
            joinColumns = @JoinColumn(name = "decision_id"))
    @Column(name = "doc_type", length = 64)
    private List<String> targetAllowedDocumentTypes = new ArrayList<>();

    /** 接受时回传的、受益人确认的受影响字段集合（须与修订完全一致）。 */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "amend_decision_field",
            joinColumns = @JoinColumn(name = "decision_id"))
    @Column(name = "field_name", length = 32)
    private List<String> acceptedFields = new ArrayList<>();

    @Column(name = "decided_by", nullable = false, length = 64)
    private String decidedBy;

    @Column(name = "reason", length = 1024)
    private String reason;

    @Column(name = "decided_at", nullable = false)
    private OffsetDateTime decidedAt;

    protected AmendmentDecision() {
    }

    /** 拒绝决定。 */
    public AmendmentDecision(String eventNo, CreditAmendment amendment,
                             String decidedBy, String reason) {
        this.eventNo = eventNo;
        this.amendment = amendment;
        this.accepted = false;
        this.decidedBy = decidedBy;
        this.reason = reason;
        this.decidedAt = OffsetDateTime.now();
    }

    /** 接受决定：携带与修订完全一致的目标内容与受影响字段。 */
    public AmendmentDecision(String eventNo,
                             CreditAmendment amendment,
                             BigDecimal targetMaxAmount,
                             LocalDate targetExpiryDate,
                             List<String> targetAllowedDocumentTypes,
                             List<String> acceptedFields,
                             String decidedBy) {
        this.eventNo = eventNo;
        this.amendment = amendment;
        this.accepted = true;
        this.targetMaxAmount = targetMaxAmount;
        this.targetExpiryDate = targetExpiryDate;
        this.targetAllowedDocumentTypes = new ArrayList<>(targetAllowedDocumentTypes);
        this.acceptedFields = new ArrayList<>(acceptedFields);
        this.decidedBy = decidedBy;
        this.decidedAt = OffsetDateTime.now();
    }

    public SortedSet<String> acceptedFieldSet() {
        return new TreeSet<>(acceptedFields);
    }

    public Long getId() {
        return id;
    }

    public String getEventNo() {
        return eventNo;
    }

    public CreditAmendment getAmendment() {
        return amendment;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public BigDecimal getTargetMaxAmount() {
        return targetMaxAmount;
    }

    public LocalDate getTargetExpiryDate() {
        return targetExpiryDate;
    }

    public List<String> getTargetAllowedDocumentTypes() {
        return Collections.unmodifiableList(targetAllowedDocumentTypes);
    }

    public List<String> getAcceptedFields() {
        return Collections.unmodifiableList(acceptedFields);
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }
}
