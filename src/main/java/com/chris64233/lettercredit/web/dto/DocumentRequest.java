package com.chris64233.lettercredit.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 单据摘要请求。
 */
public record DocumentRequest(
        @NotBlank @Size(max = 64) String documentType,
        @Min(1) int copies,
        @Size(max = 512) String description) {
}
