package com.chris64233.lettercredit.web.dto;

import com.chris64233.lettercredit.domain.DocumentSummary;

public record DocumentSummaryDto(String docType, String referenceNo, String note) {

    public DocumentSummary toEntity() {
        return new DocumentSummary(docType, referenceNo, note);
    }

    public static DocumentSummaryDto from(DocumentSummary doc) {
        return new DocumentSummaryDto(doc.getDocType(), doc.getReferenceNo(), doc.getNote());
    }
}
