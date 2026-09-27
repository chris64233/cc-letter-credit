package com.chris64233.lettercredit.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record SubmitPresentationRequest(String externalPresentationNo,
                                        BigDecimal amount,
                                        List<DocumentSummaryDto> documents,
                                        String reviewer) {
}
