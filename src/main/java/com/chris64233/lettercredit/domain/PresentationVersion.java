package com.chris64233.lettercredit.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 交单的一个审核版本：包含本次提交的单据摘要与不可修改的差异清单。
 * 版本创建后不再变更；补交单据产生新版本，旧版本保留。
 */
@Entity
@Table(name = "presentation_version",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_presentation_version_no",
                columnNames = {"presentation_id", "versionNo"}))
public class PresentationVersion {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_id", nullable = false)
    private Presentation presentation;

    @Column(nullable = false)
    private int versionNo;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "presentation_version_document",
            joinColumns = @JoinColumn(name = "presentation_version_id"))
    @OrderColumn(name = "seq")
    private List<DocumentSummary> documents = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "presentation_version_discrepancy",
            joinColumns = @JoinColumn(name = "presentation_version_id"))
    @Column(name = "discrepancy", nullable = false)
    @OrderColumn(name = "seq")
    private List<String> discrepancies = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewResult result;

    @Column(nullable = false)
    private String reviewer;

    @Column(nullable = false)
    private Instant createdAt;

    protected PresentationVersion() {
    }

    public PresentationVersion(Presentation presentation, int versionNo,
                               List<DocumentSummary> documents, List<String> discrepancies,
                               String reviewer, Instant createdAt) {
        this.presentation = presentation;
        this.versionNo = versionNo;
        this.documents = new ArrayList<>(documents);
        this.discrepancies = new ArrayList<>(discrepancies);
        this.result = discrepancies.isEmpty() ? ReviewResult.CLEAN : ReviewResult.DISCREPANT;
        this.reviewer = reviewer;
        this.createdAt = createdAt;
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

    public List<DocumentSummary> getDocuments() {
        return List.copyOf(documents);
    }

    public List<String> getDiscrepancies() {
        return List.copyOf(discrepancies);
    }

    public ReviewResult getResult() {
        return result;
    }

    public String getReviewer() {
        return reviewer;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
