package com.chris64233.lettercredit.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 信用证余额视图。
 */
public record CreditBalanceView(String creditNo,
                                String beneficiary,
                                String currency,
                                BigDecimal maxAmount,
                                BigDecimal acceptedAmount,
                                BigDecimal availableAmount,
                                LocalDate expiryDate,
                                List<String> allowedDocumentTypes,
                                int currentVersionNo,
                                long version) {
}
