package com.chris64233.lettercredit.web.dto;

import com.chris64233.lettercredit.domain.PendingPresentationPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 受益人修订决定请求。
 *
 * <p>{@code accepted=true} 接受时，{@code acceptedChangedFields} 必须与修订实际变化
 * 字段集合完全一致；存在尚未承兑交单时 {@code pendingPresentationPolicy} 必填。
 * {@code decisionEventNo} 全局唯一，作为决定事件的幂等键。</p>
 */
public record AmendmentDecisionRequest(
        @NotBlank @Size(max = 48) String decisionEventNo,
        @NotNull Boolean accepted,
        List<@NotBlank String> acceptedChangedFields,
        PendingPresentationPolicy pendingPresentationPolicy,
        @NotBlank @Size(max = 64) String decidedBy,
        @Size(max = 1024) String remark) {
}
