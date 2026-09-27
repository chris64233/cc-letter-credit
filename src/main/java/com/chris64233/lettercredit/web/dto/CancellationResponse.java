package com.chris64233.lettercredit.web.dto;

import com.chris64233.lettercredit.domain.Cancellation;

import java.time.Instant;

public record CancellationResponse(Long id,
                                   Long acceptanceId,
                                   String reason,
                                   String operator,
                                   Instant cancelledAt) {

    public static CancellationResponse from(Cancellation c) {
        return new CancellationResponse(c.getId(), c.getAcceptance().getId(),
                c.getReason(), c.getOperator(), c.getCancelledAt());
    }
}
