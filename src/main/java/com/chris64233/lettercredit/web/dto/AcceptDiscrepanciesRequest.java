package com.chris64233.lettercredit.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 申请人接受差异请求：接受范围必须与当前版本差异键完全一致。
 */
public record AcceptDiscrepanciesRequest(
        @NotEmpty List<@NotBlank String> acceptedKeys,
        @NotBlank @Size(max = 64) String acceptedBy) {
}
