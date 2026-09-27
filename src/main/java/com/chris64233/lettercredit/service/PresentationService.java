package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.DiscrepancyDecision;
import com.chris64233.lettercredit.domain.DocumentSummary;
import com.chris64233.lettercredit.domain.LetterCredit;
import com.chris64233.lettercredit.domain.Presentation;
import com.chris64233.lettercredit.domain.ReviewVersion;
import com.chris64233.lettercredit.exception.BusinessException;
import com.chris64233.lettercredit.exception.ErrorCode;
import com.chris64233.lettercredit.repository.DiscrepancyDecisionRepository;
import com.chris64233.lettercredit.repository.LetterCreditRepository;
import com.chris64233.lettercredit.repository.PresentationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 交单与审核版本服务。
 *
 * <p>首次交单生成审核版本 1；未承兑交单补交单据时生成新版本，旧版本原样保留。
 * 审核在信用证当前余额与有效期快照上进行，差异清单随版本固化。</p>
 */
@Service
public class PresentationService {

    private final PresentationRepository presentationRepository;
    private final LetterCreditRepository creditRepository;
    private final DiscrepancyDecisionRepository decisionRepository;
    private final ReviewEngine reviewEngine;

    public PresentationService(PresentationRepository presentationRepository,
                               LetterCreditRepository creditRepository,
                               DiscrepancyDecisionRepository decisionRepository,
                               ReviewEngine reviewEngine) {
        this.presentationRepository = presentationRepository;
        this.creditRepository = creditRepository;
        this.decisionRepository = decisionRepository;
        this.reviewEngine = reviewEngine;
    }

    /**
     * 首次交单：登记交单并立即形成审核版本 1。
     */
    @Transactional
    public PresentationView present(String presentationNo,
                                    String creditNo,
                                    BigDecimal amount,
                                    LocalDate presentationDate,
                                    List<DocumentSummary> documents) {
        LetterCredit credit = creditRepository.findByCreditNo(creditNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREDIT_NOT_FOUND,
                        "信用证不存在: " + creditNo));
        if (presentationRepository.existsByPresentationNo(presentationNo)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PRESENTATION_NO,
                    "外部交单号已存在: " + presentationNo);
        }

        Presentation presentation = new Presentation(presentationNo, credit, amount,
                credit.getCurrency(), presentationDate);
        presentation.addVersion(documents,
                reviewEngine.review(credit, amount, presentationDate, documents));
        presentationRepository.save(presentation);
        return toView(presentation);
    }

    /**
     * 补交单据（未承兑交单）：以累积单据集合重新审核，产生新版本，旧版本保留。
     */
    @Transactional
    public PresentationView supplement(String presentationNo, List<DocumentSummary> addedDocuments) {
        Presentation presentation = getPresentationForUpdate(presentationNo);
        if (presentation.isAccepted()) {
            throw new BusinessException(ErrorCode.PRESENTATION_ALREADY_ACCEPTED,
                    "交单 " + presentationNo + " 已承兑，不允许补交单据");
        }

        List<DocumentSummary> accumulated = new ArrayList<>(
                presentation.latestVersion().getDocuments());
        accumulated.addAll(addedDocuments);

        LetterCredit credit = presentation.getCredit();
        presentation.addVersion(accumulated,
                reviewEngine.review(credit, presentation.getAmount(),
                        presentation.getPresentationDate(), accumulated));
        presentationRepository.save(presentation);
        return toView(presentation);
    }

    @Transactional(readOnly = true)
    public PresentationView getByNo(String presentationNo) {
        return toView(getPresentation(presentationNo));
    }

    @Transactional(readOnly = true)
    public List<PresentationView> listByCredit(String creditNo) {
        LetterCredit credit = creditRepository.findByCreditNo(creditNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREDIT_NOT_FOUND,
                        "信用证不存在: " + creditNo));
        return presentationRepository
                .findByCreditIdOrderByPresentationDateAscIdAsc(credit.getId())
                .stream().map(this::toView).toList();
    }

    private Presentation getPresentation(String presentationNo) {
        return presentationRepository.findByPresentationNo(presentationNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRESENTATION_NOT_FOUND,
                        "交单不存在: " + presentationNo));
    }

    private Presentation getPresentationForUpdate(String presentationNo) {
        Presentation presentation = getPresentation(presentationNo);
        return presentationRepository.findByIdForUpdate(presentation.getId()).orElseThrow();
    }

    private PresentationView toView(Presentation presentation) {
        List<ReviewVersionView> versionViews = presentation.getReviewVersions().stream()
                .map(this::toVersionView).toList();
        return new PresentationView(presentation.getId(), presentation.getPresentationNo(),
                presentation.getCredit().getCreditNo(), presentation.getAmount(),
                presentation.getCurrency(), presentation.getPresentationDate(),
                presentation.getStatus(), presentation.latestVersion().getVersionNo(),
                versionViews);
    }

    private ReviewVersionView toVersionView(ReviewVersion version) {
        Optional<DiscrepancyDecision> decision =
                decisionRepository.findByReviewVersionId(version.getId());
        // 事务内物化为独立列表，避免视图脱离会话后触发懒加载。
        return new ReviewVersionView(version.getId(), version.getVersionNo(),
                version.getReviewedAt(), List.copyOf(version.getDocuments()),
                List.copyOf(version.getDiscrepancies()),
                version.isClean(), decision.isPresent());
    }
}
