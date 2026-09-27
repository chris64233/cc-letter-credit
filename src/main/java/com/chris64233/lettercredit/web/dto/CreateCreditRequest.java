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
 * 开立信用证请求。
 */
public record CreateCreditRequest(
        @NotBlank @Size(max = 32) String creditNo,
        @NotBlank @Size(max = 128) String beneficiary,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @Positive BigDecimal maxAmount,
        @NotNull LocalDate expiryDate,
        @NotEmpty List<@NotBlank @Size(max = 64) String> allowedDocumentTypes) {
}
