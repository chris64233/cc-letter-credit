package com.chris64233.lettercredit.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 承兑请求。支持部分承兑（金额可小于交单金额）。
 *
 * <p>{@code expectedReviewVersionNo} / {@code expectedCreditVersion} 用于
 * 乐观并发控制：审核结果或信用证余额在调用方读取后发生变化时，承兑失败。</p>
 */
public record AcceptRequest(
        @NotNull @Positive BigDecimal amount,
        Integer expectedReviewVersionNo,
        Long expectedCreditVersion,
        @NotBlank @Size(max = 64) String acceptedBy) {
}
