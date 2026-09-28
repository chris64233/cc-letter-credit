package com.chris64233.lettercredit.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 申请人取消修订请求：须给出处理人与原因，申请与决定记录保留。
 */
public record CancelAmendmentRequest(
        @NotBlank @Size(max = 64) String cancelledBy,
        @NotBlank @Size(max = 1024) String reason) {
}
