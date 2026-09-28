package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.CreditVersionKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 信用证版本视图：固化的条款与冻结的剩余金额快照，不可修改。
 */
public record CreditVersionView(int versionNo,
                                CreditVersionKind kind,
                                BigDecimal maxAmount,
                                LocalDate expiryDate,
                                List<String> allowedDocumentTypes,
                                BigDecimal frozenAcceptedAmount,
                                BigDecimal frozenAvailableAmount,
                                OffsetDateTime effectiveAt) {
}
