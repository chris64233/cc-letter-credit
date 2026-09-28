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
 * <p>未承兑交单可多次补交单据，每次补交产生新的 {@link ReviewVersion}；
 * 已承兑交单状态冻结，只允许通过独立撤销决定处理。</p>
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

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /** 交单日期（银行实际收到单据的日期），用于有效期判定。 */
    @Column(name = "presentation_date", nullable = false)
    private java.time.LocalDate presentationDate;

    /**
     * 交单时所依据的信用证版本号。交单始终绑定到交单时点的当前版本：
     * 后续修订不改变该归属，按旧版本保留的交单继续占用旧版本额度，
     * 其审核规则（有效期、单据清单）也以该版本为准。
     */
    @Column(name = "credit_version_no", nullable = false)
    private int creditVersionNo;

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
                        BigDecimal amount,
                        String currency,
                        java.time.LocalDate presentationDate,
                        int creditVersionNo) {
        this.presentationNo = presentationNo;
        this.credit = credit;
        this.amount = amount;
        this.currency = currency;
        this.presentationDate = presentationDate;
        this.creditVersionNo = creditVersionNo;
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

    /** 修订生效且申请人选择“撤回后按新版本补交”时，未承兑交单转入撤回终态。 */
    public void markWithdrawn() {
        this.status = PresentationStatus.WITHDRAWN;
    }

    public boolean isAccepted() {
        return status == PresentationStatus.ACCEPTED;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public java.time.LocalDate getPresentationDate() {
        return presentationDate;
    }

    public int getCreditVersionNo() {
        return creditVersionNo;
    }

    public PresentationStatus getStatus() {
        return status;
    }

    public List<ReviewVersion> getReviewVersions() {
        return Collections.unmodifiableList(reviewVersions);
    }
}
