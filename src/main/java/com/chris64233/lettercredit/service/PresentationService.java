package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.AmendmentStatus;
import com.chris64233.lettercredit.domain.CreditVersion;
import com.chris64233.lettercredit.domain.DiscrepancyDecision;
import com.chris64233.lettercredit.domain.DocumentSummary;
import com.chris64233.lettercredit.domain.LetterCredit;
import com.chris64233.lettercredit.domain.Presentation;
import com.chris64233.lettercredit.domain.ReviewVersion;
import com.chris64233.lettercredit.exception.BusinessException;
import com.chris64233.lettercredit.exception.ErrorCode;
import com.chris64233.lettercredit.repository.CreditAmendmentRepository;
import com.chris64233.lettercredit.repository.CreditVersionRepository;
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
 * 交单在创建时绑定信用证<strong>当前业务版本</strong>：有效期与允许单据清单
 * 始终按绑定版本审核（修订后保留在旧版本上的交单继续适用旧条款，按新版本
 * 重新交单的新交单才适用新条款）；金额可用余额则是信用证级统一信封，
 * 以当前最高金额扣减全版本承兑累计。</p>
 */
@Service
public class PresentationService {

    private final PresentationRepository presentationRepository;
    private final LetterCreditRepository creditRepository;
    private final CreditVersionRepository creditVersionRepository;
    private final CreditAmendmentRepository amendmentRepository;
    private final DiscrepancyDecisionRepository decisionRepository;
    private final ReviewEngine reviewEngine;

    public PresentationService(PresentationRepository presentationRepository,
                               LetterCreditRepository creditRepository,
                               CreditVersionRepository creditVersionRepository,
                               CreditAmendmentRepository amendmentRepository,
                               DiscrepancyDecisionRepository decisionRepository,
                               ReviewEngine reviewEngine) {
        this.presentationRepository = presentationRepository;
        this.creditRepository = creditRepository;
        this.creditVersionRepository = creditVersionRepository;
        this.amendmentRepository = amendmentRepository;
        this.decisionRepository = decisionRepository;
        this.reviewEngine = reviewEngine;
    }

    /**
     * 首次交单：登记交单并立即形成审核版本 1。交单绑定信用证当前版本。
     * 信用证存在活动修订（版本冻结中）时暂不受理新交单。
     */
    @Transactional
    public PresentationView present(String presentationNo,
                                    String creditNo,
                                    BigDecimal amount,
                                    LocalDate presentationDate,
                                    List<DocumentSummary> documents) {
        LetterCredit found = creditRepository.findByCreditNo(creditNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREDIT_NOT_FOUND,
                        "信用证不存在: " + creditNo));
        // 锁定信用证行：与修订提出/生效事务串行化，防止在版本冻结窗口内
        // 产生归属不明的新交单。
        LetterCredit credit = creditRepository.findByIdForUpdate(found.getId()).orElseThrow();
        if (amendmentRepository.existsByCreditIdAndStatus(credit.getId(),
                AmendmentStatus.PROPOSED)) {
            throw new BusinessException(ErrorCode.AMENDMENT_PENDING_PRESENTATION_BLOCKED,
                    "信用证 " + creditNo + " 存在处理中的修订，版本冻结期间暂不受理新交单；"
                            + "修订接受后按新版本交单，拒绝/取消后按当前版本交单");
        }
        if (presentationRepository.existsByPresentationNo(presentationNo)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PRESENTATION_NO,
                    "外部交单号已存在: " + presentationNo);
        }

        int creditVersionNo = credit.getCurrentVersionNo();
        Presentation presentation = new Presentation(presentationNo, credit, amount,
                credit.getCurrency(), presentationDate, creditVersionNo);
        presentation.addVersion(documents,
                reviewEngine.review(creditVersion(credit.getId(), creditVersionNo),
                        credit.availableAmount(),
                        amount, presentationDate, documents));
        presentationRepository.save(presentation);
        return toView(presentation);
    }

    /**
     * 补交单据（未承兑、未撤回交单）：以累积单据集合重新审核，产生新版本，
     * 旧版本保留。审核仍按交单绑定的信用证版本条款。
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
                    "交单 " + presentationNo + " 已随信用证修订撤回，"
                            + "请按新版本重新交单");
        }

        List<DocumentSummary> accumulated = new ArrayList<>(
                presentation.latestVersion().getDocuments());
        accumulated.addAll(addedDocuments);

        LetterCredit credit = presentation.getCredit();
        int creditVersionNo = presentation.getCreditVersionNo();
        presentation.addVersion(accumulated,
                reviewEngine.review(creditVersion(credit.getId(), creditVersionNo),
                        credit.availableAmount(),
                        presentation.getAmount(), presentation.getPresentationDate(),
                        accumulated));
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

    private CreditVersion creditVersion(Long creditId, int versionNo) {
        return creditVersionRepository.findByCreditIdAndVersionNo(creditId, versionNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERSION_NOT_FOUND,
                        "信用证版本不存在: 版本 " + versionNo));
    }

    private PresentationView toView(Presentation presentation) {
        List<ReviewVersionView> versionViews = presentation.getReviewVersions().stream()
                .map(this::toVersionView).toList();
        return new PresentationView(presentation.getId(), presentation.getPresentationNo(),
                presentation.getCredit().getCreditNo(), presentation.getAmount(),
                presentation.getCurrency(), presentation.getPresentationDate(),
                presentation.getStatus(), presentation.getCreditVersionNo(),
                presentation.latestVersion().getVersionNo(), versionViews);
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
