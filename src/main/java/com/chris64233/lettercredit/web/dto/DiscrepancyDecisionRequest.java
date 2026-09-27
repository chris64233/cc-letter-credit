package com.chris64233.lettercredit.web.dto;

import java.util.List;

public record DiscrepancyDecisionRequest(int versionNo,
                                         List<String> acceptedDiscrepancies,
                                         String decidedBy) {
}
