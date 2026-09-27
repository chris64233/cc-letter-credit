package com.chris64233.lettercredit.service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 差异接受决定视图。
 */
public record DiscrepancyDecisionView(Long id,
                                      Long reviewVersionId,
                                      int versionNo,
                                      String presentationNo,
                                      List<String> acceptedKeys,
                                      String acceptedBy,
                                      OffsetDateTime decidedAt) {
}
