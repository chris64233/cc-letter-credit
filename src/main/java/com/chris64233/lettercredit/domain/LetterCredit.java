package com.chris64233.lettercredit.domain;

import com.chris64233.lettercredit.exception.BusinessException;
import com.chris64233.lettercredit.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 跟单信用证。
 *
 * <p>信用证编号、受益人、币种等身份信息固定；<strong>可变条款</strong>
 * （最高金额、有效期、允许单据类型）固化在不可变的 {@link CreditVersion} 中：
 * 开证生成版本 1，修订接受生效后追加新版本，{@link #currentVersionNo} 指向当前版本。
 * 旧版本与既有承兑永久保留、不可修改。</p>
 *
 * <p>余额采用全证统一口径：{@code acceptedAmount} 为全部未撤销承兑的累计金额，
 * {@link #availableAmount()} 以<strong>当前版本</strong>的最高金额为顶。承兑通过
 * {@link #reserve(BigDecimal)} 占用、撤销通过 {@link #release(BigDecimal)} 恢复；
 * {@code version} 为 JPA 乐观锁，修订生效与承兑并发时以信用证行锁 + 实时余额重校验
 * 串行化，杜绝基于旧余额快照的超额承兑。</p>
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

    /** 已承兑占用金额（全证、全版本口径），累计净承兑不超过当前版本最高金额。 */
    @Column(name = "accepted_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal acceptedAmount = BigDecimal.ZERO;

    /** 当前信用证版本号，从 1 递增。 */
    @Column(name = "current_version_no", nullable = false)
    private int currentVersionNo;

    @OneToMany(mappedBy = "credit", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("versionNo ASC")
    private List<CreditVersion> versions = new ArrayList<>();

    /** JPA 乐观锁版本：余额一变，基于旧版本的承兑/修订立即失败。 */
    @Version
    private long version;

    protected LetterCredit() {
    }

    public LetterCredit(String creditNo, String beneficiary, String currency) {
        this.creditNo = creditNo;
        this.beneficiary = beneficiary;
        this.currency = currency;
    }

    /**
     * 开证时附加初始版本（版本 1），冻结开证当时的剩余金额（= 最高金额）。
     */
    public CreditVersion attachInitialVersion(BigDecimal maxAmount,
                                              java.time.LocalDate expiryDate,
                                              List<String> allowedDocumentTypes) {
        if (currentVersionNo != 0) {
            throw new IllegalStateException("信用证 " + creditNo + " 已存在初始版本");
        }
        CreditVersion initial = new CreditVersion(this, 1, CreditVersionKind.INITIAL,
                maxAmount, expiryDate, allowedDocumentTypes, BigDecimal.ZERO, null);
        versions.add(initial);
        currentVersionNo = 1;
        return initial;
    }

    /**
     * 修订生效：以修订后的条款追加新版本，并以<strong>实时重算</strong>的累计承兑
     * 金额冻结剩余快照。旧版本原样保留。
     */
    public CreditVersion appendAmendedVersion(BigDecimal maxAmount,
                                              java.time.LocalDate expiryDate,
                                              List<String> allowedDocumentTypes,
                                              Amendment amendment) {
        CreditVersion next = new CreditVersion(this, currentVersionNo + 1,
                CreditVersionKind.AMENDED, maxAmount, expiryDate, allowedDocumentTypes,
                acceptedAmount, amendment);
        versions.add(next);
        currentVersionNo = next.getVersionNo();
        return next;
    }

    /**
     * 占用可用金额（部分承兑）。以当前版本最高金额为顶，余额不足时抛业务异常。
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

    public BigDecimal availableAmount() {
        return getCurrentVersion().getMaxAmount().subtract(acceptedAmount);
    }

    public CreditVersion getCurrentVersion() {
        return versions.stream()
                .filter(v -> v.getVersionNo() == currentVersionNo)
                .findAny()
                .orElseThrow(() -> new IllegalStateException(
                        "信用证 " + creditNo + " 当前版本 " + currentVersionNo + " 不存在"));
    }

    public CreditVersion getVersion(int versionNo) {
        return versions.stream()
                .filter(v -> v.getVersionNo() == versionNo)
                .findAny()
                .orElseThrow(() -> new BusinessException(ErrorCode.VERSION_NOT_FOUND,
                        "信用证 " + creditNo + " 不存在版本 " + versionNo));
    }

    public boolean allows(String documentType) {
        return getCurrentVersion().allows(documentType);
    }

    public boolean isExpiredOn(java.time.LocalDate presentationDate) {
        return getCurrentVersion().isExpiredOn(presentationDate);
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

    public BigDecimal getAcceptedAmount() {
        return acceptedAmount;
    }

    public int getCurrentVersionNo() {
        return currentVersionNo;
    }

    public List<CreditVersion> getVersions() {
        return Collections.unmodifiableList(versions);
    }

    public long getVersion() {
        return version;
    }

    /* ---- 当前版本条款的便捷委托，保持调用点直观 ---- */

    public BigDecimal getMaxAmount() {
        return getCurrentVersion().getMaxAmount();
    }

    public java.time.LocalDate getExpiryDate() {
        return getCurrentVersion().getExpiryDate();
    }

    public List<String> getAllowedDocumentTypes() {
        return getCurrentVersion().getAllowedDocumentTypes();
    }
}
