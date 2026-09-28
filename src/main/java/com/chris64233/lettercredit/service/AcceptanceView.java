package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.AcceptanceStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 承兑台账视图。撤销信息完整保留（处理人、原因、时间）。
 *
 * @param creditVersionNo 承兑额度所归属的信用证版本号
 */
public record AcceptanceView(Long id,
                             String acceptanceNo,
                             String creditNo,
                             String presentationNo,
                             int reviewVersionNo,
                             BigDecimal amount,
                             String currency,
                             String acceptedBy,
                             OffsetDateTime acceptedAt,
                             AcceptanceStatus status,
                             int creditVersionNo,
                             long creditVersionAtAcceptance,
                             String reversedBy,
                             String reversalReason,
                             OffsetDateTime reversedAt) {
}
