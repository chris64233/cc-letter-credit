package com.chris64233.lettercredit.web.dto;

import java.util.List;

public record SupplementRequest(List<DocumentSummaryDto> documents, String reviewer) {
}
