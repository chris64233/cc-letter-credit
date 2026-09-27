package com.chris64233.lettercredit.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 跟单信用证：记录受益人、币种、最高金额、有效期和允许的单据类型。
 * 可用金额随承兑扣减、随撤销恢复；乐观锁保证并发承兑不超额。
 */
@Entity
@Table(name = "letter_credit")
public class LetterCredit {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false, unique = true)
    private String lcNumber;

    @Column(nullable = false)
    private String beneficiary;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal maxAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal availableAmount;

    @Column(nullable = false)
    private LocalDate expiryDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "letter_credit_allowed_doc_type",
            joinColumns = @JoinColumn(name = "letter_credit_id"))
    @Column(name = "doc_type", nullable = false)
    private Set<String> allowedDocumentTypes = new LinkedHashSet<>();

    @Version
    private long version;

    protected LetterCredit() {
    }

    public LetterCredit(String lcNumber, String beneficiary, String currency,
                        BigDecimal maxAmount, LocalDate expiryDate, Set<String> allowedDocumentTypes) {
        this.lcNumber = lcNumber;
        this.beneficiary = beneficiary;
        this.currency = currency;
        this.maxAmount = maxAmount;
        this.availableAmount = maxAmount;
        this.expiryDate = expiryDate;
        this.allowedDocumentTypes = new LinkedHashSet<>(allowedDocumentTypes);
    }

    public Long getId() {
        return id;
    }

    public String getLcNumber() {
        return lcNumber;
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

    public BigDecimal getAvailableAmount() {
        return availableAmount;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public Set<String> getAllowedDocumentTypes() {
        return Set.copyOf(allowedDocumentTypes);
    }

    public long getVersion() {
        return version;
    }

    public void debit(BigDecimal amount) {
        this.availableAmount = this.availableAmount.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        this.availableAmount = this.availableAmount.add(amount);
    }
}
