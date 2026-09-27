package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.Discrepancy;
import com.chris64233.lettercredit.domain.DocumentSummary;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 审核版本视图：单据快照 + 固化的差异清单。
 */
public record ReviewVersionView(Long id,
                                int versionNo,
                                OffsetDateTime reviewedAt,
                                List<DocumentSummary> documents,
                                List<Discrepancy> discrepancies,
                                boolean clean,
                                boolean discrepancyAccepted) {
}
