package com.chris64233.lettercredit.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 补交单据请求：追加的单据与既有单据合并后重新审核，产生新版本。
 */
public record SupplementRequest(
        @NotEmpty List<@Valid DocumentRequest> documents) {
}
