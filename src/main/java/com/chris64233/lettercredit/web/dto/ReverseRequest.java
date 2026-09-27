package com.chris64233.lettercredit.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 独立撤销决定请求：必须给出完整原因与处理人。
 */
public record ReverseRequest(
        @NotBlank @Size(max = 64) String reversedBy,
        @NotBlank @Size(max = 1024) String reason) {
}
