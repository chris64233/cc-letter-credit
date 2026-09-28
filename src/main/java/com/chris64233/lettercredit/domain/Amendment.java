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
 * 信用证修订（修改申请）。
 *
 * <p>由申请人提出，可修改最高金额、有效期、允许单据类型三类条款中的至少一项。
 * 创建时以信用证当前版本为基线（{@code baseVersionNo}），并冻结当时的信用证
 * 版本号与剩余金额快照。修订须经受益人接受才生效：接受即追加新信用证版本；
 * 受益人拒绝或申请人取消后，申请与决定原样保留，但当前信用证版本不变。</p>
 *
 * <p>{@code amendmentNo} 为修订业务幂等键（全局唯一），{@code amendmentSeq} 为
 * 同一信用证内的修订序号。受益人决定事件号、取消事件号分别唯一、各自幂等。</p>
 */
@Entity
public class Amendment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 修订业务编号，全局唯一（创建幂等键）。 */
    @Column(name = "amendment_no", nullable = false, unique = true, length = 40)
    private String amendmentNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_id", nullable = false)
    private LetterCredit credit;

    /** 同一信用证内修订序号，从 1 递增，不受取消/拒绝影响。 */
    @Column(name = "amendment_seq", nullable = false)
    private int amendmentSeq;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AmendmentStatus status = AmendmentStatus.PROPOSED;

    /** 修订基线信用证版本号；仅对该基线版本有效。 */
    @Column(name = "base_version_no", nullable = false)
    private int baseVersionNo;

    /** 申请人（开证申请人）。 */
    @Column(name = "proposed_by", nullable = false, length = 64)
    private String proposedBy;

    @Column(name = "proposed_at", nullable = false)
    private OffsetDateTime proposedAt;

    /* ---- 修订目标条款（至少一项与基线不同） ---- */

    @Column(name = "new_max_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal newMaxAmount;

    @Column(name = "new_expiry_date", nullable = false)
    private LocalDate newExpiryDate;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "amendment_doc_type", joinColumns = @JoinColumn(name = "amendment_id"))
    @Column(name = "doc_type", nullable = false, length = 64)
    private List<String> newAllowedDocumentTypes = new ArrayList<>();

    /* ---- 创建时冻结的版本与剩余金额快照 ---- */

    @Column(name = "frozen_accepted_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal frozenAcceptedAmount;

    @Column(name = "frozen_available_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal frozenAvailableAmount;

    /** 创建时基线版本下尚未承兑的交单数量（留痕）。 */
    @Column(name = "frozen_pending_presentations", nullable = false)
    private int frozenPendingPresentations;

    /* ---- 取消留痕（拒绝决定存于 AmendmentDecision） ---- */

    /** 取消事件号，全局唯一（取消幂等键）；未取消为 null。 */
    @Column(name = "cancel_event_no", unique = true, length = 48)
    private String cancelEventNo;

    @Column(name = "cancelled_by", length = 64)
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 1024)
    private String cancellationReason;

    /** 乐观锁：并发决定/取消时后到事务失败。 */
    @Version
    private long version;

    protected Amendment() {
    }

    public Amendment(String amendmentNo,
                     LetterCredit credit,
                     int amendmentSeq,
                     int baseVersionNo,
                     String proposedBy,
                     BigDecimal newMaxAmount,
                     LocalDate newExpiryDate,
                     List<String> newAllowedDocumentTypes,
                     BigDecimal frozenAcceptedAmount,
                     BigDecimal frozenAvailableAmount,
                     int frozenPendingPresentations) {
        this.amendmentNo = amendmentNo;
        this.credit = credit;
        this.amendmentSeq = amendmentSeq;
        this.baseVersionNo = baseVersionNo;
        this.proposedBy = proposedBy;
        this.newMaxAmount = newMaxAmount;
        this.newExpiryDate = newExpiryDate;
        this.newAllowedDocumentTypes = new ArrayList<>(newAllowedDocumentTypes);
        this.frozenAcceptedAmount = frozenAcceptedAmount;
        this.frozenAvailableAmount = frozenAvailableAmount;
        this.frozenPendingPresentations = frozenPendingPresentations;
        this.proposedAt = OffsetDateTime.now();
    }

    /**
     * 计算修订相对基线版本实际发生变化的字段键集合。
     */
    public SortedSet<String> changedFields(CreditVersion base) {
        SortedSet<String> keys = new TreeSet<>();
        if (newMaxAmount.compareTo(base.getMaxAmount()) != 0) {
            keys.add(AmendmentField.MAX_AMOUNT);
        }
        if (!newExpiryDate.equals(base.getExpiryDate())) {
            keys.add(AmendmentField.EXPIRY_DATE);
        }
        if (!new TreeSet<>(newAllowedDocumentTypes)
                .equals(new TreeSet<>(base.getAllowedDocumentTypes()))) {
            keys.add(AmendmentField.ALLOWED_DOCUMENT_TYPES);
        }
        return keys;
    }

    /** 登记取消留痕；状态校验由服务层完成。 */
    public void markCancelled(String eventNo, String cancelledBy, String reason) {
        this.status = AmendmentStatus.CANCELLED;
        this.cancelEventNo = eventNo;
        this.cancelledBy = cancelledBy;
        this.cancellationReason = reason;
        this.cancelledAt = OffsetDateTime.now();
    }

    /** 受益人接受后标记生效（新版本由服务层追加）。 */
    public void markEffective() {
        this.status = AmendmentStatus.EFFECTIVE;
    }

    /** 受益人拒绝。 */
    public void markRejected() {
        this.status = AmendmentStatus.REJECTED;
    }

    public boolean isProposed() {
        return status == AmendmentStatus.PROPOSED;
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

    public int getAmendmentSeq() {
        return amendmentSeq;
    }

    public AmendmentStatus getStatus() {
        return status;
    }

    public int getBaseVersionNo() {
        return baseVersionNo;
    }

    public String getProposedBy() {
        return proposedBy;
    }

    public OffsetDateTime getProposedAt() {
        return proposedAt;
    }

    public BigDecimal getNewMaxAmount() {
        return newMaxAmount;
    }

    public LocalDate getNewExpiryDate() {
        return newExpiryDate;
    }

    public List<String> getNewAllowedDocumentTypes() {
        return Collections.unmodifiableList(newAllowedDocumentTypes);
    }

    public BigDecimal getFrozenAcceptedAmount() {
        return frozenAcceptedAmount;
    }

    public BigDecimal getFrozenAvailableAmount() {
        return frozenAvailableAmount;
    }

    public int getFrozenPendingPresentations() {
        return frozenPendingPresentations;
    }

    public String getCancelEventNo() {
        return cancelEventNo;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public long getVersion() {
        return version;
    }
}
