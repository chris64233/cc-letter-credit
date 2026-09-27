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
import jakarta.persistence.OneToOne;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 申请人（开证申请人）对某个审核版本差异的接受决定。
 *
 * <p>决定一旦建立即不可修改；承兑时校验接受范围与该版本当前差异
 * <strong>完全一致</strong>（不多不少），差异集合与决定时刻不一致则承兑失败。</p>
 */
@Entity
public class DiscrepancyDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 每个审核版本至多有一个差异接受决定。 */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_version_id", nullable = false, unique = true)
    private ReviewVersion reviewVersion;

    /** 申请人明确接受的差异键集合，必须与版本差异键集合完全相等。 */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "disc_decision_item", joinColumns = @JoinColumn(name = "decision_id"))
    @Column(name = "accepted_key", nullable = false, length = 128)
    private List<String> acceptedKeys = new ArrayList<>();

    @Column(name = "accepted_by", nullable = false, length = 64)
    private String acceptedBy;

    @Column(name = "decided_at", nullable = false)
    private OffsetDateTime decidedAt;

    protected DiscrepancyDecision() {
    }

    public DiscrepancyDecision(ReviewVersion reviewVersion,
                               List<String> acceptedKeys,
                               String acceptedBy) {
        this.reviewVersion = reviewVersion;
        this.acceptedKeys = new ArrayList<>(acceptedKeys);
        this.acceptedBy = acceptedBy;
        this.decidedAt = OffsetDateTime.now();
    }

    public SortedSet<String> acceptedKeySet() {
        return new TreeSet<>(acceptedKeys);
    }

    public Long getId() {
        return id;
    }

    public ReviewVersion getReviewVersion() {
        return reviewVersion;
    }

    public List<String> getAcceptedKeys() {
        return Collections.unmodifiableList(acceptedKeys);
    }

    public String getAcceptedBy() {
        return acceptedBy;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }
}
