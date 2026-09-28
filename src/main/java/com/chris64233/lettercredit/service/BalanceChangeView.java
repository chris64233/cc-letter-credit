package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.BalanceChangeType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 信用证余额变动流水视图。
 */
public record BalanceChangeView(String refNo,
                                BalanceChangeType changeType,
                                int creditVersionNo,
                                BigDecimal maxAmountBefore,
                                BigDecimal maxAmountAfter,
                                BigDecimal acceptedBefore,
                                BigDecimal acceptedAfter,
                                BigDecimal availableBefore,
                                BigDecimal availableAfter,
                                OffsetDateTime occurredAt) {
}
