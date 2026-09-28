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
 * <p>开证时生成版本 1；修订经受益人接受生效时，按修订内容生成新版本，
 * 旧版本转入 {@link CreditVersionStatus#SUPERSEDED} 且内容永不改变。
 * 版本固化该版本的最高金额、有效期与允许单据类型，作为：</p>
 * <ul>
 *   <li>版本差异查询的依据；</li>
 *   <li>交单与承兑的归属（交单按其交单时所依据的版本号绑定）；</li>
 *   <li>承兑额度校验的分版本预算（绑定旧版本的交单只能占用旧版本额度）。</li>
 * </ul>
 */
@Entity
public class CreditVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_id", nullable = false)
    private LetterCredit credit;

    /** 版本序号，同一信用证内从 1 递增。 */
    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private CreditVersionStatus status = CreditVersionStatus.CURRENT;

    @Column(name = "max_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxAmount;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "credit_version_doc_type",
            joinColumns = @JoinColumn(name = "credit_version_id"))
    @Column(name = "doc_type", nullable = false, length = 64)
    private List<String> allowedDocumentTypes = new ArrayList<>();

    /** 版本生成时间（开证或修订生效）。 */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** 由哪一笔修订生效而产生；版本 1 为 null。 */
    @Column(name = "source_amendment_no", length = 40)
    private String sourceAmendmentNo;

    protected CreditVersion() {
    }

    public CreditVersion(LetterCredit credit,
                         int versionNo,
                         BigDecimal maxAmount,
                         LocalDate expiryDate,
                         List<String> allowedDocumentTypes,
                         String sourceAmendmentNo) {
        this.credit = credit;
        this.versionNo = versionNo;
        this.maxAmount = maxAmount;
        this.expiryDate = expiryDate;
        this.allowedDocumentTypes = new ArrayList<>(allowedDocumentTypes);
        this.sourceAmendmentNo = sourceAmendmentNo;
        this.createdAt = OffsetDateTime.now();
    }

    /** 修订生效、新版本生成时，本版本被取代。 */
    public void markSuperseded() {
        this.status = CreditVersionStatus.SUPERSEDED;
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

    public CreditVersionStatus getStatus() {
        return status;
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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getSourceAmendmentNo() {
        return sourceAmendmentNo;
    }
}
