package com.chris64233.lettercredit.domain;

import com.chris64233.lettercredit.exception.BusinessException;
import com.chris64233.lettercredit.exception.ErrorCode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 跟单信用证。
 *
 * <p>记录受益人、币种、最高金额、有效期与允许的单据类型。
 * 承兑通过 {@link #reserve(BigDecimal)} 占用可用金额，撤销通过
 * {@link #release(BigDecimal)} 恢复；{@code version} 为 JPA 乐观锁，
 * 并发承兑超额时由数据库行版本冲突兜底。</p>
 */
@Entity
public class LetterCredit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 信用证业务编号，唯一。 */
    @Column(name = "credit_no", nullable = false, unique = true, length = 32)
    private String creditNo;

    @Column(name = "beneficiary", nullable = false, length = 128)
    private String beneficiary;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** 信用证最高金额。 */
    @Column(name = "max_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxAmount;

    /** 已承兑占用金额，累计承兑不超过最高金额。 */
    @Column(name = "accepted_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal acceptedAmount = BigDecimal.ZERO;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "lc_allowed_doc_type", joinColumns = @JoinColumn(name = "credit_id"))
    @Column(name = "doc_type", nullable = false, length = 64)
    private List<String> allowedDocumentTypes = new ArrayList<>();

    /**
     * 当前生效的信用证版本号（对应 {@link CreditVersion#getVersionNo()}）。
     * 修订生效时递增；与 {@link #version}（JPA 乐观锁）是两个不同概念。
     */
    @Column(name = "current_version_no", nullable = false)
    private int currentVersionNo = 1;

    /** JPA 乐观锁版本：余额一变，基于旧版本的承兑立即失败。 */
    @Version
    private long version;

    protected LetterCredit() {
    }

    public LetterCredit(String creditNo,
                        String beneficiary,
                        String currency,
                        BigDecimal maxAmount,
                        LocalDate expiryDate,
                        List<String> allowedDocumentTypes) {
        this.creditNo = creditNo;
        this.beneficiary = beneficiary;
        this.currency = currency;
        this.maxAmount = maxAmount;
        this.expiryDate = expiryDate;
        this.allowedDocumentTypes = new ArrayList<>(allowedDocumentTypes);
    }

    /**
     * 占用可用金额（部分承兑）。统一金额信封：当前最高金额 − 全版本承兑累计
     * 必须足够，否则抛业务异常，不产生任何变更。
     */
    public void reserve(BigDecimal amount) {
        if (availableAmount().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.AVAILABLE_BALANCE_INSUFFICIENT,
                    "信用证 " + creditNo + " 可用余额 " + availableAmount()
                            + " 不足，无法承兑 " + amount);
        }
        acceptedAmount = acceptedAmount.add(amount);
    }

    /**
     * 撤销承兑后恢复余额。
     */
    public void release(BigDecimal amount) {
        acceptedAmount = acceptedAmount.subtract(amount);
    }

    /**
     * 修订生效：以修订内容更新信用证当前条款并切换到新版本。
     *
     * <p>{@code acceptedAmount}（实际承兑占用）保持不变——既有承兑不可修改；
     * 降低最高金额的合法性（不得低于已承兑累计）由服务在加锁后重新校验。</p>
     */
    public void applyAmendment(BigDecimal newMaxAmount,
                               LocalDate newExpiryDate,
                               List<String> newAllowedDocumentTypes,
                               int newVersionNo) {
        this.maxAmount = newMaxAmount;
        this.expiryDate = newExpiryDate;
        this.allowedDocumentTypes = new ArrayList<>(newAllowedDocumentTypes);
        this.currentVersionNo = newVersionNo;
    }

    public BigDecimal availableAmount() {
        return maxAmount.subtract(acceptedAmount);
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

    public String getCreditNo() {
        return creditNo;
    }

    public String getBeneficiary() {
        return beneficiary;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public BigDecimal getAcceptedAmount() {
        return acceptedAmount;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public List<String> getAllowedDocumentTypes() {
        return Collections.unmodifiableList(allowedDocumentTypes);
    }

    public int getCurrentVersionNo() {
        return currentVersionNo;
    }

    public long getVersion() {
        return version;
    }
}
