package com.chris64233.lettercredit.web.dto;

import com.chris64233.lettercredit.domain.Presentation;
import com.chris64233.lettercredit.domain.PresentationStatus;

import java.math.BigDecimal;

public record PresentationResponse(Long id,
                                   Long letterCreditId,
                                   String externalPresentationNo,
                                   BigDecimal amount,
                                   PresentationStatus status,
                                   int currentVersionNo) {

    public static PresentationResponse from(Presentation p) {
        return new PresentationResponse(p.getId(), p.getLetterCredit().getId(),
                p.getExternalPresentationNo(), p.getAmount(), p.getStatus(), p.getCurrentVersionNo());
    }
}
