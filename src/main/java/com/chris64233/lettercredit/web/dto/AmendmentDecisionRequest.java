package com.chris64233.lettercredit.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 受益人修订决定请求。
 *
 * <p>{@code accepted=true} 时必须回传完整的修订后目标条款（最高金额、有效期、
 * 允许单据类型）与受影响字段集合，服务端校验其与当前修订版本<strong>完全
 * 一致</strong>后方可生效；{@code accepted=false} 为拒绝，可附原因。
 * {@code eventNo} 为决定事件幂等键。</p>
 */
public record AmendmentDecisionRequest(
        @NotBlank @Size(max = 40) String eventNo,
        boolean accepted,
        BigDecimal targetMaxAmount,
        LocalDate targetExpiryDate,
        List<@NotBlank @Size(max = 64) String> targetAllowedDocumentTypes,
        List<@NotBlank @Size(max = 32) String> acceptedFields,
        @NotBlank @Size(max = 64) String decidedBy,
        @Size(max = 1024) String reason) {
}
