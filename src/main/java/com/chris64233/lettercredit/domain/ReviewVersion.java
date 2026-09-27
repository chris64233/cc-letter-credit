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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 交单的一次审核版本。首次交单生成版本 1，此后每补交一次单据生成新版本，
 * 旧版本永久保留。
 *
 * <p>版本创建时把当次提交的单据摘要与差异清单一并快照固化，
 * 之后任何操作都不得修改；差异接受决定 {@link DiscrepancyDecision}
 * 与承兑 {@link Acceptance} 都绑定到具体版本。</p>
 */
@Entity
public class ReviewVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_id", nullable = false)
    private Presentation presentation;

    /** 版本序号，同一交单内从 1 递增。 */
    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "reviewed_at", nullable = false)
    private OffsetDateTime reviewedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "review_document", joinColumns = @JoinColumn(name = "review_version_id"))
    private List<DocumentSummary> documents = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "review_discrepancy", joinColumns = @JoinColumn(name = "review_version_id"))
    private List<Discrepancy> discrepancies = new ArrayList<>();

    protected ReviewVersion() {
    }

    ReviewVersion(Presentation presentation,
                  int versionNo,
                  List<DocumentSummary> documents,
                  List<Discrepancy> discrepancies) {
        this.presentation = presentation;
        this.versionNo = versionNo;
        this.documents = new ArrayList<>(documents);
        this.discrepancies = new ArrayList<>(discrepancies);
        this.reviewedAt = OffsetDateTime.now();
    }

    public boolean isClean() {
        return discrepancies.isEmpty();
    }

    /**
     * 当前差异集合的稳定标识键，用于差异接受范围的精确比对。
     */
    public java.util.SortedSet<String> discrepancyKeys() {
        java.util.SortedSet<String> keys = new java.util.TreeSet<>();
        for (Discrepancy discrepancy : discrepancies) {
            keys.add(discrepancy.key());
        }
        return keys;
    }

    public Long getId() {
        return id;
    }

    public Presentation getPresentation() {
        return presentation;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public List<DocumentSummary> getDocuments() {
        return Collections.unmodifiableList(documents);
    }

    public List<Discrepancy> getDiscrepancies() {
        return Collections.unmodifiableList(discrepancies);
    }
}
