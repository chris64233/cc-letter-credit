package com.chris64233.lettercredit.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record CreateLetterCreditRequest(String lcNumber,
                                        String beneficiary,
                                        String currency,
                                        BigDecimal maxAmount,
                                        LocalDate expiryDate,
                                        Set<String> allowedDocumentTypes) {
}
