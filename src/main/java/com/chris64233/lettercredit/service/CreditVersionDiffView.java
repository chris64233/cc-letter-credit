package com.chris64233.lettercredit.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.SortedSet;

/**
 * 两个信用证版本之间的条款差异视图。
 *
 * @param changedFields 发生变化的字段键集合（MAX_AMOUNT / EXPIRY_DATE / ALLOWED_DOCUMENT_TYPES）
 * @param maxAmountChanged 最高金额是否变化
 * @param maxAmountBefore 变化前最高金额
 * @param maxAmountAfter 变化后最高金额
 * @param expiryDateChanged 有效期是否变化
 * @param expiryDateBefore 变化前有效期
 * @param expiryDateAfter 变化后有效期
 * @param docTypesChanged 允许单据类型是否变化
 * @param docTypesBefore 变化前单据类型
 * @param docTypesAfter 变化后单据类型
 */
public record CreditVersionDiffView(int fromVersionNo,
                                    int toVersionNo,
                                    SortedSet<String> changedFields,
                                    boolean maxAmountChanged,
                                    BigDecimal maxAmountBefore,
                                    BigDecimal maxAmountAfter,
                                    boolean expiryDateChanged,
                                    LocalDate expiryDateBefore,
                                    LocalDate expiryDateAfter,
                                    boolean docTypesChanged,
                                    List<String> docTypesBefore,
                                    List<String> docTypesAfter) {
}
