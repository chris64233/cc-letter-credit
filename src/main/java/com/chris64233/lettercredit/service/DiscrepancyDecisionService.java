package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.DiscrepancyDecision;
import com.chris64233.lettercredit.domain.Presentation;
import com.chris64233.lettercredit.domain.ReviewVersion;
import com.chris64233.lettercredit.exception.BusinessException;
import com.chris64233.lettercredit.exception.ErrorCode;
import com.chris64233.lettercredit.repository.DiscrepancyDecisionRepository;
import com.chris64233.lettercredit.repository.PresentationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 差异接受决定服务。
 *
 * <p>申请人只能针对当前最新审核版本登记一次接受决定，且接受范围必须与
 * 版本差异键集合<strong>完全一致</strong>（不多不少）。无差异版本无需也不允许登记。</p>
 */
@Service
public class DiscrepancyDecisionService {

    private final DiscrepancyDecisionRepository decisionRepository;
    private final PresentationRepository presentationRepository;

    public DiscrepancyDecisionService(DiscrepancyDecisionRepository decisionRepository,
                                      PresentationRepository presentationRepository) {
        this.decisionRepository = decisionRepository;
        this.presentationRepository = presentationRepository;
    }

    /**
     * 登记申请人对交单当前版本差异的接受决定。
     *
     * @param acceptedKeys 申请人明确接受的差异键；必须与当前版本差异完全一致
     */
    @Transactional
    public DiscrepancyDecisionView accept(String presentationNo,
                                          List<String> acceptedKeys,
                                          String acceptedBy) {
        Presentation presentation = presentationRepository.findByPresentationNo(presentationNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRESENTATION_NOT_FOUND,
                        "交单不存在: " + presentationNo));
        if (presentation.isAccepted()) {
            throw new BusinessException(ErrorCode.PRESENTATION_ALREADY_ACCEPTED,
                    "交单 " + presentationNo + " 已承兑，差异决定不可变更");
        }
        if (presentation.isWithdrawn()) {
            throw new BusinessException(ErrorCode.PRESENTATION_WITHDRAWN,
                    "交单 " + presentationNo + " 已随信用证修订撤回，"
                            + "不能登记差异决定");
        }

        ReviewVersion latest = presentation.latestVersion();
        if (latest.isClean()) {
            throw new BusinessException(ErrorCode.NO_DISCREPANCY_TO_ACCEPT,
                    "交单 " + presentationNo + " 当前审核版本无差异，无需接受差异");
        }

        SortedSet<String> requested = new TreeSet<>(acceptedKeys);
        SortedSet<String> actual = latest.discrepancyKeys();
        if (!requested.equals(actual)) {
            throw new BusinessException(ErrorCode.DISCREPANCY_SCOPE_MISMATCH,
                    "接受差异范围与当前差异版本不一致：接受 " + requested + "，当前差异 " + actual);
        }

        var existing = decisionRepository.findByReviewVersionId(latest.getId());
        if (existing.isPresent()) {
            if (!existing.get().acceptedKeySet().equals(requested)) {
                throw new BusinessException(ErrorCode.DISCREPANCY_DECISION_CONFLICT,
                        "该审核版本已存在内容不一致的差异接受决定");
            }
            return toView(presentation, existing.get());
        }

        DiscrepancyDecision decision = new DiscrepancyDecision(latest,
                List.copyOf(requested), acceptedBy);
        decision = decisionRepository.save(decision);
        return toView(presentation, decision);
    }

    @Transactional(readOnly = true)
    public List<DiscrepancyDecisionView> listByPresentation(String presentationNo) {
        Presentation presentation = presentationRepository.findByPresentationNo(presentationNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRESENTATION_NOT_FOUND,
                        "交单不存在: " + presentationNo));
        return presentation.getReviewVersions().stream()
                .map(v -> decisionRepository.findByReviewVersionId(v.getId()).orElse(null))
                .filter(java.util.Objects::nonNull)
                .map(d -> toView(presentation, d))
                .toList();
    }

    private DiscrepancyDecisionView toView(Presentation presentation, DiscrepancyDecision decision) {
        return new DiscrepancyDecisionView(decision.getId(),
                decision.getReviewVersion().getId(),
                decision.getReviewVersion().getVersionNo(),
                presentation.getPresentationNo(),
                List.copyOf(decision.getAcceptedKeys()), decision.getAcceptedBy(),
                decision.getDecidedAt());
    }
}
