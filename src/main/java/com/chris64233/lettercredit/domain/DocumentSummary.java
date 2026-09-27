package com.chris64233.lettercredit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 单据摘要：交单时每份单据的类型、编号与备注。
 */
@Embeddable
public class DocumentSummary {

    @Column(nullable = false)
    private String docType;

    private String referenceNo;

    private String note;

    protected DocumentSummary() {
    }

    public DocumentSummary(String docType, String referenceNo, String note) {
        this.docType = docType;
        this.referenceNo = referenceNo;
        this.note = note;
    }

    public String getDocType() {
        return docType;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public String getNote() {
        return note;
    }
}
