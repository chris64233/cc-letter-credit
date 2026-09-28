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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 信用证的一个不可变版本。
 *
 * <p>开证生成版本 1（{@link CreditVersionKind#INITIAL}）；修订经受益人接受生效后
 * 追加新版本（{@link CreditVersionKind#AMENDED}）。版本固化当时的最高金额、有效期、
 * 允许单据类型与<strong>剩余金额快照</strong>，此后任何操作都不得修改；
 * 交单（{@link Presentation}）与承兑（{@link Acceptance}）都绑定到具体信用证版本。</p>
 *
 * <p>{@code frozenAcceptedAmount} / {@code frozenAvailableAmount} 是版本生成时刻
 * 全证累计已承兑金额与剩余金额的留痕快照：修订创建时在修订单内冻结一次，
 * 生效时以重算后的真实余额生成新版本，承兑并发以信用证行锁与实时余额为准，
 * 不以旧快照作为扣款依据。</p>
 */
@Entity
public class CreditVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_id", nullable = false)
    private LetterCredit credit;

    /** 版本号，同一信用证内从 1 递增。 */
    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private CreditVersionKind kind;

    @Column(name = "max_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxAmount;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "credit_version_doc_type", joinColumns = @JoinColumn(name = "credit_version_id"))
    @Column(name = "doc_type", nullable = false, length = 64)
    private List<String> allowedDocumentTypes = new ArrayList<>();

    /** 版本生成时刻的全证累计已承兑金额快照（留痕）。 */
    @Column(name = "frozen_accepted_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal frozenAcceptedAmount;

    /** 版本生成时刻的剩余金额（最高金额 − 累计已承兑）快照（留痕）。 */
    @Column(name = "frozen_available_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal frozenAvailableAmount;

    @Column(name = "effective_at", nullable = false)
    private OffsetDateTime effectiveAt;

    /** 产生本版本的修订；初始版本为 null。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "amendment_id")
    private Amendment amendment;

    protected CreditVersion() {
    }

    public CreditVersion(LetterCredit credit,
                         int versionNo,
                         CreditVersionKind kind,
                         BigDecimal maxAmount,
                         LocalDate expiryDate,
                         List<String> allowedDocumentTypes,
                         BigDecimal frozenAcceptedAmount,
                         Amendment amendment) {
        this.credit = credit;
        this.versionNo = versionNo;
        this.kind = kind;
        this.maxAmount = maxAmount;
        this.expiryDate = expiryDate;
        this.allowedDocumentTypes = new ArrayList<>(allowedDocumentTypes);
        this.frozenAcceptedAmount = frozenAcceptedAmount;
        this.frozenAvailableAmount = maxAmount.subtract(frozenAcceptedAmount);
        this.effectiveAt = OffsetDateTime.now();
        this.amendment = amendment;
    }

    public boolean allows(String documentType) {
        return allowedDocumentTypes.contains(documentType);
    }

    public boolean isExpiredOn(LocalDate presentationDate) {
        return presentationDate.isAfter(expiryDate);
    }

    public Long getId() {
        return id;
    }

    public LetterCredit getCredit() {
        return credit;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public CreditVersionKind getKind() {
        return kind;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public List<String> getAllowedDocumentTypes() {
        return Collections.unmodifiableList(allowedDocumentTypes);
    }

    public BigDecimal getFrozenAcceptedAmount() {
        return frozenAcceptedAmount;
    }

    public BigDecimal getFrozenAvailableAmount() {
        return frozenAvailableAmount;
    }

    public OffsetDateTime getEffectiveAt() {
        return effectiveAt;
    }

    public Amendment getAmendment() {
        return amendment;
    }
}
