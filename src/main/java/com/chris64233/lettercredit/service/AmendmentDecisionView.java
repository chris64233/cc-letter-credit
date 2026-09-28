package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.PendingPresentationPolicy;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 受益人修订决定视图。
 */
public record AmendmentDecisionView(String decisionEventNo,
                                    String amendmentNo,
                                    boolean accepted,
                                    List<String> acceptedChangedFields,
                                    PendingPresentationPolicy pendingPresentationPolicy,
                                    int pendingPresentationsAtDecision,
                                    String decidedBy,
                                    OffsetDateTime decidedAt,
                                    String remark) {
}
