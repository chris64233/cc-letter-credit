package com.chris64233.lettercredit.web.dto;

import com.chris64233.lettercredit.domain.LetterCredit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record LetterCreditResponse(Long id,
                                   String lcNumber,
                                   String beneficiary,
                                   String currency,
                                   BigDecimal maxAmount,
                                   BigDecimal availableAmount,
                                   LocalDate expiryDate,
                                   Set<String> allowedDocumentTypes,
                                   long version) {

    public static LetterCreditResponse from(LetterCredit lc) {
        return new LetterCreditResponse(lc.getId(), lc.getLcNumber(), lc.getBeneficiary(),
                lc.getCurrency(), lc.getMaxAmount(), lc.getAvailableAmount(),
                lc.getExpiryDate(), lc.getAllowedDocumentTypes(), lc.getVersion());
    }
}
