package com.chris64233.lettercredit.web.dto;

import com.chris64233.lettercredit.domain.Acceptance;
import com.chris64233.lettercredit.domain.Cancellation;

import java.math.BigDecimal;
import java.time.Instant;

public record AcceptanceResponse(Long id,
                                 Long presentationId,
                                 int versionNo,
                                 BigDecimal amount,
                                 String operator,
                                 Instant acceptedAt,
                                 boolean cancelled,
                                 CancellationResponse cancellation) {

    public static AcceptanceResponse from(Acceptance a, Cancellation c) {
        return new AcceptanceResponse(a.getId(), a.getPresentation().getId(), a.getVersionNo(),
                a.getAmount(), a.getOperator(), a.getAcceptedAt(),
                c != null, c == null ? null : CancellationResponse.from(c));
    }
}
