package com.chris64233.lettercredit.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 首次交单请求。
 */
public record CreatePresentationRequest(
        @NotBlank @Size(max = 40) String presentationNo,
        @NotBlank @Size(max = 32) String creditNo,
        @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate presentationDate,
        @NotEmpty List<@Valid DocumentRequest> documents) {
}
