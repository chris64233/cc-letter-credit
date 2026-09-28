package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.AcceptanceStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 承兑台账视图。撤销信息完整保留（处理人、原因、时间）。
 */
public record AcceptanceView(Long id,
                             String acceptanceNo,
                             String creditNo,
                             String presentationNo,
                             int reviewVersionNo,
                             int creditVersionNo,
                             BigDecimal amount,
                             String currency,
                             String acceptedBy,
                             OffsetDateTime acceptedAt,
                             AcceptanceStatus status,
                             long creditVersionAtAcceptance,
                             String reversedBy,
                             String reversalReason,
                             OffsetDateTime reversedAt) {
}
