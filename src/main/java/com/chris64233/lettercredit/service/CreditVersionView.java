package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.CreditVersionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 信用证版本视图：固化条款（版本差异对比依据）+ 归属该版本的承兑累计。
 *
 * <p>金额额度是信用证级的<strong>统一信封</strong>：所有版本的承兑共享
 * 修订后的当前最高金额（见余额查询的 {@code availableAmount}）；
 * {@code versionAcceptedAmount} 仅用于归属展示，不构成独立额度上限。</p>
 */
public record CreditVersionView(int versionNo,
                                CreditVersionStatus status,
                                BigDecimal maxAmount,
                                LocalDate expiryDate,
                                List<String> allowedDocumentTypes,
                                String sourceAmendmentNo,
                                OffsetDateTime createdAt,
                                BigDecimal versionAcceptedAmount) {
}
