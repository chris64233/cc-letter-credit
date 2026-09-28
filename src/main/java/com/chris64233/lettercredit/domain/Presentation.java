package com.chris64233.lettercredit.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次交单。外部交单号全局唯一，作为承兑幂等键。
 *
 * <p>交单发生时绑定当时的信用证版本 {@link #creditVersion}：各审核版本与承兑
 * 都在该信用证版本条款（有效期、允许单据类型）下处理。修订生效后，该交单
 * 所依据版本保持不变（旧版本不可修改），由受益人在接受修订时明确是继续按
 * 旧版本处理还是撤回重交。</p>
 *
 * <p>未承兑交单可多次补交单据，每次补交产生新的 {@link ReviewVersion}；
 * 已承兑交单状态冻结，只允许通过独立撤销决定处理；
 * 被修订生效撤回的交单进入 {@link PresentationStatus#WITHDRAWN} 终态。</p>
 */
@Entity
public class Presentation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 外部交单号，全局唯一（承兑幂等键）。 */
    @Column(name = "presentation_no", nullable = false, unique = true, length = 40)
    private String presentationNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_id", nullable = false)
    private LetterCredit credit;

    /** 交单时所依据的信用证版本；修订生效后也不改变。 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_version_id", nullable = false)
    private CreditVersion creditVersion;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** 交单日期（银行实际收到单据的日期），用于有效期判定。 */
    @Column(name = "presentation_date", nullable = false)
    private java.time.LocalDate presentationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PresentationStatus status = PresentationStatus.PRESENTED;

    @OneToMany(mappedBy = "presentation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("versionNo ASC")
    private List<ReviewVersion> reviewVersions = new ArrayList<>();

    protected Presentation() {
    }

    public Presentation(String presentationNo,
                        LetterCredit credit,
                        CreditVersion creditVersion,
                        BigDecimal amount,
                        String currency,
                        java.time.LocalDate presentationDate) {
        this.presentationNo = presentationNo;
        this.credit = credit;
        this.creditVersion = creditVersion;
        this.amount = amount;
        this.currency = currency;
        this.presentationDate = presentationDate;
    }

    /**
     * 追加一个审核版本（首次交单或补交单据）。
     */
    public ReviewVersion addVersion(List<DocumentSummary> documents, List<Discrepancy> discrepancies) {
        ReviewVersion version = new ReviewVersion(this, reviewVersions.size() + 1,
                List.copyOf(documents), List.copyOf(discrepancies));
        reviewVersions.add(version);
        return version;
    }

    public ReviewVersion latestVersion() {
        return reviewVersions.get(reviewVersions.size() - 1);
    }

    public void markAccepted() {
        this.status = PresentationStatus.ACCEPTED;
    }

    /** 修订生效且受益人选择撤回重交时，未承兑交单进入撤回终态。 */
    public void withdraw() {
        if (status != PresentationStatus.PRESENTED) {
            throw new IllegalStateException("交单 " + presentationNo + " 当前状态 " + status
                    + " 不允许撤回");
        }
        this.status = PresentationStatus.WITHDRAWN;
    }

    public boolean isAccepted() {
        return status == PresentationStatus.ACCEPTED;
    }

    public boolean isPending() {
        return status == PresentationStatus.PRESENTED;
    }

    public boolean isWithdrawn() {
        return status == PresentationStatus.WITHDRAWN;
    }

    public Long getId() {
        return id;
    }

    public String getPresentationNo() {
        return presentationNo;
    }

    public LetterCredit getCredit() {
        return credit;
    }

    public CreditVersion getCreditVersion() {
        return creditVersion;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public java.time.LocalDate getPresentationDate() {
        return presentationDate;
    }

    public PresentationStatus getStatus() {
        return status;
    }

    public List<ReviewVersion> getReviewVersions() {
        return Collections.unmodifiableList(reviewVersions);
    }
}
