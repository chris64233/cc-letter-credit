package com.chris64233.lettercredit.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 受益人修订决定视图。
 */
public record AmendmentDecisionView(Long id,
                                    String eventNo,
                                    String amendmentNo,
                                    boolean accepted,
                                    BigDecimal targetMaxAmount,
                                    LocalDate targetExpiryDate,
                                    List<String> targetAllowedDocumentTypes,
                                    List<String> acceptedFields,
                                    String decidedBy,
                                    String reason,
                                    OffsetDateTime decidedAt) {
}
