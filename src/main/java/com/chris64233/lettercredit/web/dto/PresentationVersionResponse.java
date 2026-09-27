package com.chris64233.lettercredit.web.dto;

import com.chris64233.lettercredit.domain.PresentationVersion;
import com.chris64233.lettercredit.domain.ReviewResult;

import java.time.Instant;
import java.util.List;

public record PresentationVersionResponse(Long id,
                                          int versionNo,
                                          List<DocumentSummaryDto> documents,
                                          List<String> discrepancies,
                                          ReviewResult result,
                                          String reviewer,
                                          Instant createdAt) {

    public static PresentationVersionResponse from(PresentationVersion v) {
        return new PresentationVersionResponse(v.getId(), v.getVersionNo(),
                v.getDocuments().stream().map(DocumentSummaryDto::from).toList(),
                v.getDiscrepancies(), v.getResult(), v.getReviewer(), v.getCreatedAt());
    }
}
