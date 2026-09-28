package com.chris64233.lettercredit.web.dto;

import com.chris64233.lettercredit.domain.PendingPresentationPolicy;
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
 * <p>三项条款均必填（须回传完整的修订后目标内容），至少一项与当前条款不同。
 * 当修订改变了既有未承兑交单所依据字段时，{@code pendingPolicy} 必填：</p>
 * <ul>
 *   <li>{@code KEEP_OLD_VERSION}：这些交单继续按旧版本审核承兑；</li>
 *   <li>{@code WITHDRAW_AND_RESUBMIT}：修订生效时撤回这些交单，按新版本重新交单。</li>
 * </ul>
 */
public record ProposeAmendmentRequest(
        @NotBlank @Size(max = 40) String amendmentNo,
        @NotNull @Positive BigDecimal proposedMaxAmount,
        @NotNull LocalDate proposedExpiryDate,
        @NotEmpty List<@NotBlank @Size(max = 64) String> proposedAllowedDocumentTypes,
        PendingPresentationPolicy pendingPolicy,
        @NotBlank @Size(max = 64) String proposedBy) {
}
