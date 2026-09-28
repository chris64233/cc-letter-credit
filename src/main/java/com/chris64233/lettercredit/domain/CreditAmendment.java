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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 信用证修订申请。
 *
 * <p>申请人可申请修改最高金额、有效期或允许单据类型。创建时冻结：
 * 所依据的信用证版本号、当时已承兑累计金额与剩余金额。同一信用证同时
 * 至多存在一笔 {@link AmendmentStatus#PROPOSED} 活动修订；修订号为
 * 幂等键。</p>
 *
 * <p>修订必须经受益人接受后方可生效；改变了既有未承兑交单所依据字段时，
 * 申请人还须明确这些交单 {@link PendingPresentationPolicy 继续使用旧版本
 * 还是撤回后按新版本补交}。终态（接受/拒绝/取消）后申请内容原样保留，
 * 不允许再变更。</p>
 */
@Entity
public class CreditAmendment {

    /** 可修订字段标识，同时用于决定范围比对。 */
    public static final String FIELD_MAX_AMOUNT = "MAX_AMOUNT";
    public static final String FIELD_EXPIRY_DATE = "EXPIRY_DATE";
    public static final String FIELD_ALLOWED_DOCUMENT_TYPES = "ALLOWED_DOCUMENT_TYPES";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 修订号，全局唯一，创建/重复提交的幂等键。 */
    @Column(name = "amendment_no", nullable = false, unique = true, length = 40)
    private String amendmentNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_id", nullable = false)
    private LetterCredit credit;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AmendmentStatus status = AmendmentStatus.PROPOSED;

    /** 提出修订时信用证的当前版本号，修订基于该版本。 */
    @Column(name = "base_version_no", nullable = false)
    private int baseVersionNo;

    /** 冻结：提出时已承兑累计金额。 */
    @Column(name = "frozen_accepted_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal frozenAcceptedAmount;

    /** 冻结：提出时剩余可用金额（当时最高金额 − 已承兑金额）。 */
    @Column(name = "frozen_remaining_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal frozenRemainingAmount;

    /** 申请的新最高金额；不变则与旧值相同。 */
    @Column(name = "proposed_max_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal proposedMaxAmount;

    @Column(name = "proposed_expiry_date", nullable = false)
    private LocalDate proposedExpiryDate;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "amendment_doc_type",
            joinColumns = @JoinColumn(name = "amendment_id"))
    @Column(name = "doc_type", nullable = false, length = 64)
    private List<String> proposedAllowedDocumentTypes = new ArrayList<>();

    /** 本次实际发生变化的字段集合。 */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "amendment_field",
            joinColumns = @JoinColumn(name = "amendment_id"))
    @Column(name = "field_name", nullable = false, length = 32)
    private List<String> affectedFields = new ArrayList<>();

    /**
     * 提出时快照：受本次修订影响的未承兑交单号（绑定基础版本且状态为 PRESENTED）。
     * 生效时按 {@link #pendingPolicy} 处置的正是这批交单；即使期间交单状态变化，
     * 快照仍如实记录修订提出时点的影响范围。
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "amendment_pending_presentation",
            joinColumns = @JoinColumn(name = "amendment_id"))
    @Column(name = "presentation_no", nullable = false, length = 40)
    private List<String> pendingPresentationNos = new ArrayList<>();

    /**
     * 对提出时已存在的未承兑交单的处置方式；仅当 {@link #affectedFields}
     * 非空（修订改变了交单所依据字段）且存在未承兑交单时必须指定。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "pending_policy", length = 32)
    private PendingPresentationPolicy pendingPolicy;

    @Column(name = "proposed_by", nullable = false, length = 64)
    private String proposedBy;

    @Column(name = "proposed_at", nullable = false)
    private OffsetDateTime proposedAt;

    @Column(name = "cancelled_by", length = 64)
    private String cancelledBy;

    @Column(name = "cancelled_reason", length = 1024)
    private String cancelledReason;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    /** 乐观锁：并发决定/取消时第二个事务失败回滚。 */
    @Version
    private long version;

    protected CreditAmendment() {
    }

    public CreditAmendment(String amendmentNo,
                           LetterCredit credit,
                           int baseVersionNo,
                           BigDecimal frozenAcceptedAmount,
                           BigDecimal frozenRemainingAmount,
                           BigDecimal proposedMaxAmount,
                           LocalDate proposedExpiryDate,
                           List<String> proposedAllowedDocumentTypes,
                           List<String> affectedFields,
                           List<String> pendingPresentationNos,
                           PendingPresentationPolicy pendingPolicy,
                           String proposedBy) {
        this.amendmentNo = amendmentNo;
        this.credit = credit;
        this.baseVersionNo = baseVersionNo;
        this.frozenAcceptedAmount = frozenAcceptedAmount;
        this.frozenRemainingAmount = frozenRemainingAmount;
        this.proposedMaxAmount = proposedMaxAmount;
        this.proposedExpiryDate = proposedExpiryDate;
        this.proposedAllowedDocumentTypes = new ArrayList<>(proposedAllowedDocumentTypes);
        this.affectedFields = new ArrayList<>(affectedFields);
        this.pendingPresentationNos = new ArrayList<>(pendingPresentationNos);
        this.pendingPolicy = pendingPolicy;
        this.proposedBy = proposedBy;
        this.proposedAt = OffsetDateTime.now();
    }

    public boolean isActive() {
        return status == AmendmentStatus.PROPOSED;
    }

    /**
     * 受益人接受的范围必须与当前修订版本完全一致：三项申请内容逐一相等。
     */
    public boolean targetMatches(BigDecimal maxAmount, LocalDate expiryDate,
                                 List<String> allowedDocumentTypes) {
        return proposedMaxAmount.compareTo(maxAmount) == 0
                && proposedExpiryDate.equals(expiryDate)
                && new TreeSet<>(proposedAllowedDocumentTypes)
                .equals(new TreeSet<>(allowedDocumentTypes));
    }

    public SortedSet<String> affectedFieldSet() {
        return new TreeSet<>(affectedFields);
    }

    /** 受益人接受并生效：终态。新信用证版本由服务在同事务内生成。 */
    public void markAccepted() {
        this.status = AmendmentStatus.ACCEPTED;
    }

    /** 受益人拒绝：终态，申请保留，信用证版本不变。 */
    public void markRejected() {
        this.status = AmendmentStatus.REJECTED;
    }

    /** 申请人取消：终态，申请保留，信用证版本不变。 */
    public void cancel(String cancelledBy, String reason) {
        this.status = AmendmentStatus.CANCELLED;
        this.cancelledBy = cancelledBy;
        this.cancelledReason = reason;
        this.cancelledAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getAmendmentNo() {
        return amendmentNo;
    }

    public LetterCredit getCredit() {
        return credit;
    }

    public AmendmentStatus getStatus() {
        return status;
    }

    public int getBaseVersionNo() {
        return baseVersionNo;
    }

    public BigDecimal getFrozenAcceptedAmount() {
        return frozenAcceptedAmount;
    }

    public BigDecimal getFrozenRemainingAmount() {
        return frozenRemainingAmount;
    }

    public BigDecimal getProposedMaxAmount() {
        return proposedMaxAmount;
    }

    public LocalDate getProposedExpiryDate() {
        return proposedExpiryDate;
    }

    public List<String> getProposedAllowedDocumentTypes() {
        return Collections.unmodifiableList(proposedAllowedDocumentTypes);
    }

    public List<String> getAffectedFields() {
        return Collections.unmodifiableList(affectedFields);
    }

    public List<String> getPendingPresentationNos() {
        return Collections.unmodifiableList(pendingPresentationNos);
    }

    /** 是否影响既有未承兑交单：字段确有变化且提出时存在受影响的未承兑交单。 */
    public boolean affectsPendingPresentations() {
        return !affectedFields.isEmpty() && !pendingPresentationNos.isEmpty();
    }

    public PendingPresentationPolicy getPendingPolicy() {
        return pendingPolicy;
    }

    public String getProposedBy() {
        return proposedBy;
    }

    public OffsetDateTime getProposedAt() {
        return proposedAt;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public String getCancelledReason() {
        return cancelledReason;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public long getVersion() {
        return version;
    }
}
