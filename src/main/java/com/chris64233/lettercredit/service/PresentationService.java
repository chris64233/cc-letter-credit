package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.CreditVersion;
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
import jakarta.persistence.EntityManager;
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
 * <p>首次交单生成审核版本 1，并把交单绑定到信用证<strong>当前版本</strong>；
 * 未承兑交单补交单据时按所绑定版本条款重新审核并产生新版本，旧版本原样保留。
 * 条款类差异（单据类型、有效期）固化在交单绑定的信用证版本上，修订生效不重审；
 * 余额类差异按信用证实时可用余额计算。交单与修订、承兑统一锁顺序
 * 「先信用证行」（补交为先交单行后信用证行），与修订统计未承兑交单串行。</p>
 */
@Service
public class PresentationService {

    private final PresentationRepository presentationRepository;
    private final LetterCreditRepository creditRepository;
    private final DiscrepancyDecisionRepository decisionRepository;
    private final ReviewEngine reviewEngine;
    private final EntityManager entityManager;

    public PresentationService(PresentationRepository presentationRepository,
                               LetterCreditRepository creditRepository,
                               DiscrepancyDecisionRepository decisionRepository,
                               ReviewEngine reviewEngine,
                               EntityManager entityManager) {
        this.presentationRepository = presentationRepository;
        this.creditRepository = creditRepository;
        this.decisionRepository = decisionRepository;
        this.reviewEngine = reviewEngine;
        this.entityManager = entityManager;
    }

    /**
     * 首次交单：锁定信用证行（与修订创建/承兑串行），绑定当前信用证版本，
     * 并立即形成审核版本 1。
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
        // 锁信用证行：与修订统计未承兑交单、承兑余额变更串行，读到一致的当前版本与余额。
        credit = creditRepository.findByIdForUpdate(credit.getId()).orElseThrow();
        entityManager.refresh(credit);

        CreditVersion creditVersion = credit.getCurrentVersion();
        Presentation presentation = new Presentation(presentationNo, credit, creditVersion, amount,
                credit.getCurrency(), presentationDate);
        presentation.addVersion(documents,
                reviewEngine.review(credit, creditVersion, amount, presentationDate, documents));
        presentationRepository.save(presentation);
        return toView(presentation);
    }

    /**
     * 补交单据（未承兑、未撤回交单）：按交单绑定的信用证版本条款与信用证实时余额
     * 重新审核，产生新版本，旧版本保留。
     */
    @Transactional
    public PresentationView supplement(String presentationNo, List<DocumentSummary> addedDocuments) {
        Presentation presentation = getPresentationForUpdate(presentationNo);
        if (presentation.isAccepted()) {
            throw new BusinessException(ErrorCode.PRESENTATION_ALREADY_ACCEPTED,
                    "交单 " + presentationNo + " 已承兑，不允许补交单据");
        }
        if (presentation.isWithdrawn()) {
            throw new BusinessException(ErrorCode.PRESENTATION_WITHDRAWN,
                    "交单 " + presentationNo + " 已随信用证修订生效撤回，"
                            + "不能补交，请按新版本重新交单");
        }

        // 锁信用证行读取实时余额（顺序：先交单行后信用证行，与承兑一致）。
        LetterCredit credit = creditRepository
                .findByIdForUpdate(presentation.getCredit().getId()).orElseThrow();
        CreditVersion creditVersion = presentation.getCreditVersion();

        List<DocumentSummary> accumulated = new ArrayList<>(
                presentation.latestVersion().getDocuments());
        accumulated.addAll(addedDocuments);

        presentation.addVersion(accumulated,
                reviewEngine.review(credit, creditVersion, presentation.getAmount(),
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
        Presentation locked = presentationRepository.findByIdForUpdate(presentation.getId())
                .orElseThrow();
        entityManager.refresh(locked);
        return locked;
    }

    private PresentationView toView(Presentation presentation) {
        List<ReviewVersionView> versionViews = presentation.getReviewVersions().stream()
                .map(this::toVersionView).toList();
        return new PresentationView(presentation.getId(), presentation.getPresentationNo(),
                presentation.getCredit().getCreditNo(),
                presentation.getCreditVersion().getVersionNo(), presentation.getAmount(),
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
