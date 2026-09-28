package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.AmendmentStatus;
import com.chris64233.lettercredit.domain.PendingPresentationPolicy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 信用证修订视图：申请内容、创建时冻结快照、受影响字段、
 * 既有未承兑交单处置方式与终态信息。
 */
public record AmendmentView(String amendmentNo,
                            String creditNo,
                            AmendmentStatus status,
                            int baseVersionNo,
                            BigDecimal frozenAcceptedAmount,
                            BigDecimal frozenRemainingAmount,
                            BigDecimal currentMaxAmount,
                            LocalDate currentExpiryDate,
                            List<String> currentAllowedDocumentTypes,
                            BigDecimal proposedMaxAmount,
                            LocalDate proposedExpiryDate,
                            List<String> proposedAllowedDocumentTypes,
                            List<String> affectedFields,
                            PendingPresentationPolicy pendingPolicy,
                            List<String> pendingPresentationNos,
                            String proposedBy,
                            OffsetDateTime proposedAt,
                            String cancelledBy,
                            String cancelledReason,
                            OffsetDateTime cancelledAt,
                            AmendmentDecisionView decision) {
}
