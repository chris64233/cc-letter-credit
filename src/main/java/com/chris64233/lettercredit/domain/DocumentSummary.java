package com.chris64233.lettercredit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * 交单中一份单据的摘要信息。属于审核版本快照的一部分，随版本固化、不可修改。
 */
@Embeddable
public class DocumentSummary {

    /** 单据类型，需匹配信用证允许的单据类型。 */
    @Column(name = "doc_type", nullable = false, length = 64)
    private String documentType;

    /** 单据份数。 */
    @Column(name = "copies", nullable = false)
    private int copies;

    /** 单据简述。 */
    @Column(name = "description", length = 512)
    private String description;

    protected DocumentSummary() {
    }

    public DocumentSummary(String documentType, int copies, String description) {
        this.documentType = documentType;
        this.copies = copies;
        this.description = description;
    }

    public String getDocumentType() {
        return documentType;
    }

    public int getCopies() {
        return copies;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DocumentSummary that)) {
            return false;
        }
        return copies == that.copies
                && Objects.equals(documentType, that.documentType)
                && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentType, copies, description);
    }
}
