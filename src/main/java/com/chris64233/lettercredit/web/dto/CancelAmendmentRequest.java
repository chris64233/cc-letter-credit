package com.chris64233.lettercredit.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 申请人取消修订请求。取消事件号全局唯一，作为取消操作的幂等键。
 */
public record CancelAmendmentRequest(
        @NotBlank @Size(max = 48) String cancelEventNo,
        @NotBlank @Size(max = 64) String cancelledBy,
        @NotBlank @Size(max = 1024) String reason) {
}
