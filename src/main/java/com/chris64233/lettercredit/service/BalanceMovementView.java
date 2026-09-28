package com.chris64233.lettercredit.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 信用证余额变动流水视图。
 */
public record BalanceMovementView(Long id,
                                  String type,
                                  BigDecimal amountDelta,
                                  BigDecimal acceptedAmountAfter,
                                  BigDecimal maxAmountAfter,
                                  int creditVersionNoAfter,
                                  String refNo,
                                  String operator,
                                  OffsetDateTime occurredAt) {
}
