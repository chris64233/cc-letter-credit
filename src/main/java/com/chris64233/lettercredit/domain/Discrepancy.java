package com.chris64233.lettercredit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

/**
 * 审核差异条目。审核版本生成时固化，之后不可修改。
 *
 * <p>同一差异类型下若涉及具体单据，用 {@link #documentType} 标识；
 * 信用证级别的差异该字段为 {@code null}。</p>
 */
@Embeddable
public class Discrepancy {

    @Enumerated(EnumType.STRING)
    @Column(name = "disc_code", nullable = false, length = 32)
    private DiscrepancyCode code;

    /** 差异涉及的单据类型；信用证级别差异为 null。 */
    @Column(name = "doc_type", length = 64)
    private String documentType;

    @Column(name = "message", nullable = false, length = 512)
    private String message;

    protected Discrepancy() {
    }

    public Discrepancy(DiscrepancyCode code, String documentType, String message) {
        this.code = code;
        this.documentType = documentType;
        this.message = message;
    }

    /**
     * 差异的稳定标识键。申请人接受差异时按该键精确匹配，
     * 因此不依赖可能被人工调整的描述文案。
     */
    public String key() {
        return code.name() + (documentType == null ? "" : ":" + documentType);
    }

    public DiscrepancyCode getCode() {
        return code;
    }

    /** 差异稳定标识键，同时用于 JSON 暴露，供申请人接受差异时精确引用。 */
    public String getKey() {
        return key();
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Discrepancy that)) {
            return false;
        }
        return code == that.code && Objects.equals(documentType, that.documentType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, documentType);
    }
}
