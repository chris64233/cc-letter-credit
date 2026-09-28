package com.chris64233.lettercredit.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 申请人提出信用证修订请求。
 *
 * <p>三项条款中至少一项与当前版本不同；字段为 null 表示沿用当前版本对应条款。
 * 路径中的信用证编号来自 {@code /api/credits/{creditNo}/amendments}。</p>
 */
public record CreateAmendmentRequest(
        @NotBlank @Size(max = 40) String amendmentNo,
        @NotNull @Positive BigDecimal newMaxAmount,
        @NotNull LocalDate newExpiryDate,
        @NotEmpty List<@NotBlank @Size(max = 64) String> newAllowedDocumentTypes,
        @NotBlank @Size(max = 64) String proposedBy) {
}
