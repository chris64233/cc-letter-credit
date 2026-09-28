package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.AmendmentStatus;
import com.chris64233.lettercredit.domain.PendingPresentationPolicy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.SortedSet;

/**
 * 修订视图：申请内容、冻结快照、状态与受益人决定。
 */
public record AmendmentView(String amendmentNo,
                            String creditNo,
                            int amendmentSeq,
                            AmendmentStatus status,
                            int baseVersionNo,
                            String proposedBy,
                            OffsetDateTime proposedAt,
                            BigDecimal newMaxAmount,
                            LocalDate newExpiryDate,
                            List<String> newAllowedDocumentTypes,
                            SortedSet<String> changedFields,
                            BigDecimal frozenAcceptedAmount,
                            BigDecimal frozenAvailableAmount,
                            int frozenPendingPresentations,
                            AmendmentDecisionView decision,
                            String cancelledBy,
                            OffsetDateTime cancelledAt,
                            String cancellationReason) {
}
