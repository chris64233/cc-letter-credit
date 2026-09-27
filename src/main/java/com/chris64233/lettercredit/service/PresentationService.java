package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.Acceptance;
import com.chris64233.lettercredit.domain.Cancellation;
import com.chris64233.lettercredit.domain.DiscrepancyDecision;
import com.chris64233.lettercredit.domain.DocumentSummary;
import com.chris64233.lettercredit.domain.LetterCredit;
import com.chris64233.lettercredit.domain.Presentation;
import com.chris64233.lettercredit.domain.PresentationStatus;
import com.chris64233.lettercredit.domain.PresentationVersion;
import com.chris64233.lettercredit.domain.ReviewResult;
import com.chris64233.lettercredit.repo.AcceptanceRepository;
import com.chris64233.lettercredit.repo.CancellationRepository;
import com.chris64233.lettercredit.repo.DiscrepancyDecisionRepository;
import com.chris64233.lettercredit.repo.LetterCreditRepository;
import com.chris64233.lettercredit.repo.PresentationRepository;
import com.chris64233.lettercredit.repo.PresentationVersionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 交单审核、差异处理、承兑与撤销的核心业务服务。
 *
 * <p>关键规则：
 * <ul>
 *   <li>交单号（同一信用证下的外部交单号）为幂等键，重复提交返回原交单。</li>
 *   <li>审核差异清单随版本固化，不可修改；补交单据产生新版本，旧版本保留。</li>
 *   <li>无差异交单可直接承兑；有差异交单须申请人接受与当前版本完全一致的差异。</li>
 *   <li>承兑与扣减信用证余额在同一事务内完成；信用证余额使用乐观锁，
 *       审核版本或余额变化时基于旧版本的承兑失败；并发承兑不会超额。</li>
 *   <li>承兑记录不可修改，只能通过独立的撤销决定恢复余额。</li>
 * </ul>
 */
@Service
public class PresentationService {

    private final LetterCreditRepository letterCreditRepository;
    private final PresentationRepository presentationRepository;
    private final PresentationVersionRepository versionRepository;
    private final DiscrepancyDecisionRepository decisionRepository;
    private final AcceptanceRepository acceptanceRepository;
    private final CancellationRepository cancellationRepository;

    public PresentationService(LetterCreditRepository letterCreditRepository,
                               PresentationRepository presentationRepository,
                               PresentationVersionRepository versionRepository,
                               DiscrepancyDecisionRepository decisionRepository,
                               AcceptanceRepository acceptanceRepository,
                               CancellationRepository cancellationRepository) {
        this.letterCreditRepository = letterCreditRepository;
        this.presentationRepository = presentationRepository;
        this.versionRepository = versionRepository;
        this.decisionRepository = decisionRepository;
        this.acceptanceRepository = acceptanceRepository;
        this.cancellationRepository = cancellationRepository;
    }

    // ---------- 交单与审核 ----------

    /**
     * 提交交单并生成首个审核版本。相同外部交单号重复提交时幂等返回原交单。
     */
    @Transactional
    public Presentation submitPresentation(long letterCreditId, String externalPresentationNo,
                                           BigDecimal amount, List<DocumentSummary> documents,
                                           String reviewer) {
        LetterCreditService.requireAmount(amount, "交单金额");
        if (externalPresentationNo == null || externalPresentationNo.isBlank()) {
            throw new BadRequestException("外部交单号不能为空");
        }
        var existing = presentationRepository
                .findByLetterCreditIdAndExternalPresentationNo(letterCreditId, externalPresentationNo);
        if (existing.isPresent()) {
            Presentation found = existing.get();
            if (found.getAmount().compareTo(amount) != 0) {
                throw new ConflictException("交单号已存在且金额不一致: " + externalPresentationNo);
            }
            return found;
        }
        LetterCredit lc = loadLetterCredit(letterCreditId);
        Presentation presentation = new Presentation(lc, externalPresentationNo, amount, Instant.now());
        try {
            presentationRepository.saveAndFlush(presentation);
        } catch (DataIntegrityViolationException e) {
            // 并发提交相同交单号：幂等返回已存在记录
            return presentationRepository
                    .findByLetterCreditIdAndExternalPresentationNo(letterCreditId, externalPresentationNo)
                    .orElseThrow(() -> e);
        }
        createReviewVersion(presentation, documents, reviewer);
        return presentation;
    }

    /**
     * 未承兑交单补交单据，产生新的审核版本；旧版本保留。
     */
    @Transactional
    public PresentationVersion supplementPresentation(long presentationId,
                                                      List<DocumentSummary> documents,
                                                      String reviewer) {
        Presentation presentation = loadPresentation(presentationId);
        if (presentation.getStatus() == PresentationStatus.ACCEPTED) {
            throw new ConflictException("已承兑交单不得补交单据");
        }
        return createReviewVersion(presentation, documents, reviewer);
    }

    private PresentationVersion createReviewVersion(Presentation presentation,
                                                    List<DocumentSummary> documents,
                                                    String reviewer) {
        if (documents == null || documents.isEmpty()) {
            throw new BadRequestException("交单必须包含至少一份单据");
        }
        int versionNo = presentation.getCurrentVersionNo() + 1;
        List<String> discrepancies = review(presentation.getLetterCredit(), presentation.getAmount(), documents);
        PresentationVersion version = new PresentationVersion(
                presentation, versionNo, documents, discrepancies,
                reviewer == null || reviewer.isBlank() ? "system" : reviewer, Instant.now());
        versionRepository.saveAndFlush(version);
        presentation.advanceVersion(versionNo);
        return version;
    }

    /**
     * 审核规则：生成不可修改的差异清单。
     */
    private List<String> review(LetterCredit lc, BigDecimal amount, List<DocumentSummary> documents) {
        List<String> discrepancies = new ArrayList<>();
        if (LocalDate.now().isAfter(lc.getExpiryDate())) {
            discrepancies.add("LC_EXPIRED");
        }
        if (amount.compareTo(lc.getAvailableAmount()) > 0) {
            discrepancies.add("AMOUNT_EXCEEDS_AVAILABLE");
        }
        for (DocumentSummary doc : documents) {
            if (doc.getDocType() == null || doc.getDocType().isBlank()) {
                discrepancies.add("DOCUMENT_TYPE_MISSING");
            } else if (!lc.getAllowedDocumentTypes().contains(doc.getDocType())) {
                discrepancies.add("DOCUMENT_TYPE_NOT_ALLOWED:" + doc.getDocType());
            }
        }
        return discrepancies;
    }

    // ---------- 差异决定 ----------

    /**
     * 申请人接受差异。接受范围必须与当前审核版本的差异清单完全一致。
     */
    @Transactional
    public DiscrepancyDecision decideDiscrepancies(long presentationId, int versionNo,
                                                   List<String> acceptedDiscrepancies,
                                                   String decidedBy) {
        Presentation presentation = loadPresentation(presentationId);
        if (presentation.getStatus() == PresentationStatus.ACCEPTED) {
            throw new ConflictException("交单已承兑，无法再作差异决定");
        }
        if (presentation.getCurrentVersionNo() != versionNo) {
            throw new ConflictException("审核版本已变化，当前版本为 "
                    + presentation.getCurrentVersionNo() + "，请基于最新版本决定");
        }
        PresentationVersion version = loadVersion(presentationId, versionNo);
        if (version.getResult() != ReviewResult.DISCREPANT) {
            throw new ConflictException("当前版本无差异，无需差异决定");
        }
        if (decidedBy == null || decidedBy.isBlank()) {
            throw new BadRequestException("决定人不能为空");
        }
        if (!sameDiscrepancies(acceptedDiscrepancies, version.getDiscrepancies())) {
            throw new ConflictException("接受的差异范围必须与当前差异版本完全一致");
        }
        DiscrepancyDecision decision = new DiscrepancyDecision(
                presentation, versionNo, version.getDiscrepancies(), decidedBy, Instant.now());
        return decisionRepository.saveAndFlush(decision);
    }

    private boolean sameDiscrepancies(List<String> accepted, List<String> current) {
        if (accepted == null) {
            return false;
        }
        return new TreeSet<>(accepted).equals(new TreeSet<>(current));
    }

    // ---------- 承兑与撤销 ----------

    /**
     * 承兑交单：在同一事务中扣减信用证可用金额并记录承兑结果。
     * 相同交单号、相同版本的重复承兑幂等返回原承兑记录。
     */
    @Transactional
    public Acceptance acceptPresentation(long presentationId, int versionNo, String operator) {
        Presentation presentation = loadPresentation(presentationId);
        var existing = acceptanceRepository.findByPresentationId(presentation.getId());
        if (existing.isPresent()) {
            Acceptance acceptance = existing.get();
            if (acceptance.getVersionNo() == versionNo) {
                return acceptance; // 幂等：同一交单号同一版本重复承兑
            }
            throw new ConflictException("交单已按版本 " + acceptance.getVersionNo() + " 承兑，不能重复承兑");
        }
        if (presentation.getStatus() != PresentationStatus.UNDER_REVIEW) {
            throw new ConflictException("交单当前状态不允许承兑");
        }
        if (presentation.getCurrentVersionNo() != versionNo) {
            throw new ConflictException("审核结果已变化，基于旧版本 " + versionNo + " 的承兑失败，当前版本为 "
                    + presentation.getCurrentVersionNo());
        }
        PresentationVersion version = loadVersion(presentationId, versionNo);
        if (version.getResult() == ReviewResult.DISCREPANT) {
            var decision = decisionRepository
                    .findFirstByPresentationIdAndVersionNoOrderByIdDesc(presentationId, versionNo);
            if (decision.isEmpty()
                    || !sameDiscrepancies(decision.get().getAcceptedDiscrepancies(), version.getDiscrepancies())) {
                throw new ConflictException("存在差异，须申请人接受当前版本全部差异后方可承兑");
            }
        }
        LetterCredit lc = presentation.getLetterCredit();
        if (lc.getAvailableAmount().compareTo(presentation.getAmount()) < 0) {
            throw new ConflictException("信用证可用余额不足，无法承兑");
        }
        if (operator == null || operator.isBlank()) {
            throw new BadRequestException("承兑处理人不能为空");
        }
        lc.debit(presentation.getAmount());
        Acceptance acceptance = new Acceptance(
                presentation, versionNo, presentation.getAmount(), operator, Instant.now());
        try {
            acceptanceRepository.saveAndFlush(acceptance);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("信用证余额已变化，基于旧版本的承兑失败");
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("交单已被其他事务承兑");
        }
        presentation.markAccepted();
        return acceptance;
    }

    /**
     * 撤销承兑：独立的撤销决定，恢复信用证余额并保留原因与处理人。
     * 承兑记录本身不被修改。
     */
    @Transactional
    public Cancellation cancelAcceptance(long acceptanceId, String reason, String operator) {
        Acceptance acceptance = acceptanceRepository.findById(acceptanceId)
                .orElseThrow(() -> new NotFoundException("承兑记录不存在: " + acceptanceId));
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("撤销原因不能为空");
        }
        if (operator == null || operator.isBlank()) {
            throw new BadRequestException("撤销处理人不能为空");
        }
        if (cancellationRepository.existsByAcceptanceId(acceptanceId)) {
            throw new ConflictException("该承兑已被撤销，不能重复撤销");
        }
        LetterCredit lc = acceptance.getPresentation().getLetterCredit();
        lc.credit(acceptance.getAmount());
        Cancellation cancellation = new Cancellation(acceptance, reason, operator, Instant.now());
        try {
            return cancellationRepository.saveAndFlush(cancellation);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("信用证余额已变化，撤销失败，请重试");
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("该承兑已被其他事务撤销");
        }
    }

    // ---------- 查询 ----------

    @Transactional(readOnly = true)
    public Presentation getPresentation(long presentationId) {
        return loadPresentation(presentationId);
    }

    @Transactional(readOnly = true)
    public List<PresentationVersion> listVersions(long presentationId) {
        loadPresentation(presentationId);
        return versionRepository.findByPresentationIdOrderByVersionNo(presentationId);
    }

    @Transactional(readOnly = true)
    public List<DiscrepancyDecision> listDecisions(long presentationId) {
        loadPresentation(presentationId);
        return decisionRepository.findByPresentationIdOrderById(presentationId);
    }

    /**
     * 承兑台账：信用证下全部承兑记录及其撤销决定（如有）。
     */
    @Transactional(readOnly = true)
    public List<AcceptanceLedgerEntry> listAcceptanceLedger(long letterCreditId) {
        loadLetterCredit(letterCreditId);
        return acceptanceRepository.findByPresentationLetterCreditIdOrderById(letterCreditId).stream()
                .map(a -> new AcceptanceLedgerEntry(a,
                        cancellationRepository.findByAcceptanceId(a.getId()).orElse(null)))
                .toList();
    }

    /**
     * 台账条目：承兑记录 + 可选的撤销决定。
     */
    public record AcceptanceLedgerEntry(Acceptance acceptance, Cancellation cancellation) {
        public boolean isCancelled() {
            return cancellation != null;
        }
    }

    private LetterCredit loadLetterCredit(long letterCreditId) {
        return letterCreditRepository.findById(letterCreditId)
                .orElseThrow(() -> new NotFoundException("信用证不存在: " + letterCreditId));
    }

    private Presentation loadPresentation(long presentationId) {
        return presentationRepository.findById(presentationId)
                .orElseThrow(() -> new NotFoundException("交单不存在: " + presentationId));
    }

    private PresentationVersion loadVersion(long presentationId, int versionNo) {
        return versionRepository.findByPresentationIdAndVersionNo(presentationId, versionNo)
                .orElseThrow(() -> new NotFoundException(
                        "交单 " + presentationId + " 的审核版本不存在: " + versionNo));
    }
}
