package com.chris64233.lettercredit.web.dto;

import com.chris64233.lettercredit.domain.DiscrepancyDecision;

import java.time.Instant;
import java.util.List;

public record DiscrepancyDecisionResponse(Long id,
                                          int versionNo,
                                          List<String> acceptedDiscrepancies,
                                          String decidedBy,
                                          Instant decidedAt) {

    public static DiscrepancyDecisionResponse from(DiscrepancyDecision d) {
        return new DiscrepancyDecisionResponse(d.getId(), d.getVersionNo(),
                d.getAcceptedDiscrepancies(), d.getDecidedBy(), d.getDecidedAt());
    }
}
