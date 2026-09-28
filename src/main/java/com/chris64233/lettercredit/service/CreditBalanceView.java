package com.chris64233.lettercredit.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 信用证余额视图。
 *
 * @param currentVersionNo 当前生效的信用证业务版本号（修订生效后递增）
 * @param version          JPA 乐观锁版本（余额或条款变化即递增，用于并发校验）
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
