package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.Acceptance;
import com.chris64233.lettercredit.domain.AcceptanceStatus;
import com.chris64233.lettercredit.domain.BalanceChange;
import com.chris64233.lettercredit.domain.BalanceChangeType;
import com.chris64233.lettercredit.domain.DiscrepancyDecision;
import com.chris64233.lettercredit.domain.LetterCredit;
import com.chris64233.lettercredit.domain.Presentation;
import com.chris64233.lettercredit.domain.ReviewVersion;
import com.chris64233.lettercredit.exception.BusinessException;
import com.chris64233.lettercredit.exception.ErrorCode;
import com.chris64233.lettercredit.repository.AcceptanceRepository;
import com.chris64233.lettercredit.repository.BalanceChangeRepository;
import com.chris64233.lettercredit.repository.DiscrepancyDecisionRepository;
import com.chris64233.lettercredit.repository.LetterCreditRepository;
import com.chris64233.lettercredit.repository.PresentationRepository;
import jakarta.persistence.EntityManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 承兑与撤销服务。
 *
 * <p>承兑在<strong>同一数据库事务</strong>内完成：版本校验 → 差异决定校验 →
 * 扣减信用证可用金额 → 写承兑台账 → 冻结交单 → 登记余额变动流水。通过交单行与
 * 信用证行的悲观写锁（固定顺序：先交单行后信用证行，与修订生效一致）串行化并发
 * 承兑/撤销/修订生效，配合信用证乐观锁版本兜底，保证累计承兑不超额；
 * 余额不足与金额校验全部在锁内基于<strong>实时余额</strong>重算，不使用旧快照。
 * 外部交单号作为幂等键，重复承兑不重复扣款。</p>
 */
@Service
public class AcceptanceService {

    private final AcceptanceRepository acceptanceRepository;
    private final PresentationRepository presentationRepository;
    private final LetterCreditRepository creditRepository;
    private final DiscrepancyDecisionRepository decisionRepository;
    private final BalanceChangeRepository balanceChangeRepository;
    private final EntityManager entityManager;

    public AcceptanceService(AcceptanceRepository acceptanceRepository,
                             PresentationRepository presentationRepository,
                             LetterCreditRepository creditRepository,
                             DiscrepancyDecisionRepository decisionRepository,
                             BalanceChangeRepository balanceChangeRepository,
                             EntityManager entityManager) {
        this.acceptanceRepository = acceptanceRepository;
        this.presentationRepository = presentationRepository;
        this.creditRepository = creditRepository;
        this.decisionRepository = decisionRepository;
        this.balanceChangeRepository = balanceChangeRepository;
        this.entityManager = entityManager;
    }

    /**
     * 承兑（支持部分承兑）。
     *
     * @param presentationNo            外部交单号（幂等键）
     * @param amount                    承兑金额，0 &lt; amount &le; 交单金额
     * @param expectedReviewVersionNo   调用方依据的审核版本号；为空则默认当前最新版本
     * @param expectedCreditVersion     调用方依据的信用证余额版本；为空则不校验
     * @param acceptedBy                承兑处理人
     */
    @Transactional
    public AcceptanceView accept(String presentationNo,
                                 BigDecimal amount,
                                 Integer expectedReviewVersionNo,
                                 Long expectedCreditVersion,
                                 String acceptedBy) {
        // 固定加锁顺序：先交单行后信用证行，与修订生效/撤销一致，避免死锁。
        Presentation presentation = lockPresentation(presentationNo);
        LetterCredit credit = lockCredit(presentation.getCredit().getId());

        // 幂等：同一交单号重复承兑，原样返回既有台账，不再次扣款。
        Optional<Acceptance> existing = acceptanceRepository.findByPresentationId(presentation.getId());
        if (existing.isPresent()) {
            return toView(existing.get());
        }

        if (presentation.isWithdrawn()) {
            throw new BusinessException(ErrorCode.PRESENTATION_WITHDRAWN,
                    "交单 " + presentationNo + " 已随信用证修订生效撤回，不能承兑");
        }

        ReviewVersion latest = presentation.latestVersion();

        if (expectedReviewVersionNo != null
                && expectedReviewVersionNo != latest.getVersionNo()) {
            throw new BusinessException(ErrorCode.REVIEW_VERSION_STALE,
                    "审核版本已过期：请求依据版本 " + expectedReviewVersionNo
                            + "，当前最新版本 " + latest.getVersionNo());
        }
        if (expectedCreditVersion != null
                && expectedCreditVersion != credit.getVersion()) {
            throw new BusinessException(ErrorCode.CREDIT_VERSION_STALE,
                    "信用证余额已变化：请求依据版本 " + expectedCreditVersion
                            + "，当前版本 " + credit.getVersion());
        }

        if (!latest.isClean()) {
            DiscrepancyDecision decision = decisionRepository
                    .findByReviewVersionId(latest.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.DISCREPANCY_DECISION_REQUIRED,
                            "当前审核版本存在差异，须经申请人明确接受全部差异后方可承兑"));
            if (!decision.acceptedKeySet().equals(latest.discrepancyKeys())) {
                throw new BusinessException(ErrorCode.DISCREPANCY_SCOPE_MISMATCH,
                        "差异接受范围与当前差异版本不完全一致，拒绝承兑");
            }
        }

        if (amount == null || amount.signum() <= 0
                || amount.compareTo(presentation.getAmount()) > 0) {
            throw new BusinessException(ErrorCode.INVALID_ACCEPTANCE_AMOUNT,
                    "承兑金额必须为正且不超过交单金额 " + presentation.getAmount());
        }

        long creditVersionBefore = credit.getVersion();
        BigDecimal maxAmount = credit.getCurrentVersion().getMaxAmount();
        BigDecimal acceptedBefore = credit.getAcceptedAmount();
        try {
            // 同事务扣减余额（锁内实时余额校验）：余额不足直接抛异常，整笔回滚。
            credit.reserve(amount);

            Acceptance acceptance = new Acceptance("ACC-" + presentationNo, credit,
                    presentation, latest, amount, acceptedBy, creditVersionBefore);
            acceptance = acceptanceRepository.save(acceptance);
            presentation.markAccepted();
            balanceChangeRepository.save(new BalanceChange(credit, BalanceChangeType.ACCEPTED,
                    acceptance.getAcceptanceNo(), credit.getCurrentVersionNo(),
                    maxAmount, maxAmount, acceptedBefore, credit.getAcceptedAmount()));

            // 提前 flush，使乐观锁冲突在本事务内显式暴露。
            acceptanceRepository.flush();
            return toView(acceptance);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.CREDIT_VERSION_STALE,
                    "信用证余额并发变化，承兑失败，请基于最新余额重试");
        }
    }

    /**
     * 独立撤销决定：承兑记录本身不改写，只追加撤销状态、原因与处理人，
     * 并在同一事务恢复信用证余额、登记余额变动流水。
     */
    @Transactional
    public AcceptanceView reverse(String acceptanceNo, String reversedBy, String reason) {
        Acceptance acceptance = acceptanceRepository.findByAcceptanceNo(acceptanceNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCEPTANCE_NOT_FOUND,
                        "承兑记录不存在: " + acceptanceNo));
        if (acceptance.isReversed()) {
            throw new BusinessException(ErrorCode.ACCEPTANCE_ALREADY_REVERSED,
                    "承兑 " + acceptanceNo + " 已撤销，不能重复撤销");
        }

        lockPresentation(acceptance.getPresentation().getPresentationNo());
        LetterCredit credit = lockCredit(acceptance.getCredit().getId());

        BigDecimal maxAmount = credit.getCurrentVersion().getMaxAmount();
        BigDecimal acceptedBefore = credit.getAcceptedAmount();
        try {
            credit.release(acceptance.getAmount());
            acceptance.reverse(reversedBy, reason);
            balanceChangeRepository.save(new BalanceChange(credit, BalanceChangeType.REVERSED,
                    acceptance.getAcceptanceNo(), credit.getCurrentVersionNo(),
                    maxAmount, maxAmount, acceptedBefore, credit.getAcceptedAmount()));
            // 提前 flush：并发撤销时乐观锁冲突在本事务内显式暴露并回滚余额恢复。
            acceptanceRepository.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.ACCEPTANCE_ALREADY_REVERSED,
                    "承兑 " + acceptanceNo + " 已被并发撤销，本次操作失败");
        }
        return toView(acceptance);
    }

    @Transactional(readOnly = true)
    public AcceptanceView getByNo(String acceptanceNo) {
        return acceptanceRepository.findByAcceptanceNo(acceptanceNo)
                .map(this::toView)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCEPTANCE_NOT_FOUND,
                        "承兑记录不存在: " + acceptanceNo));
    }

    @Transactional(readOnly = true)
    public AcceptanceView getByPresentationNo(String presentationNo) {
        return acceptanceRepository.findByPresentationPresentationNo(presentationNo)
                .map(this::toView)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCEPTANCE_NOT_FOUND,
                        "交单 " + presentationNo + " 尚无承兑记录"));
    }

    /**
     * 承兑台账查询：按信用证列出全部承兑（含已撤销）。
     */
    @Transactional(readOnly = true)
    public List<AcceptanceView> ledger(String creditNo, AcceptanceStatus statusFilter) {
        LetterCredit credit = creditRepository.findByCreditNo(creditNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREDIT_NOT_FOUND,
                        "信用证不存在: " + creditNo));
        List<Acceptance> acceptances = statusFilter == null
                ? acceptanceRepository.findByCreditIdOrderByAcceptedAtAsc(credit.getId())
                : acceptanceRepository.findByCreditIdAndStatusOrderByAcceptedAtAsc(
                        credit.getId(), statusFilter);
        return acceptances.stream().map(this::toView).toList();
    }

    private Presentation lockPresentation(String presentationNo) {
        Presentation presentation = presentationRepository.findByPresentationNo(presentationNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRESENTATION_NOT_FOUND,
                        "交单不存在: " + presentationNo));
        Presentation locked = presentationRepository.findByIdForUpdate(presentation.getId())
                .orElseThrow();
        // FOR UPDATE 命中持久化上下文缓存时只加锁不刷新状态；显式 refresh 读取
        // 并发修订/承兑提交后的最新状态，杜绝基于旧状态快照的处理。
        entityManager.refresh(locked);
        return locked;
    }

    private LetterCredit lockCredit(Long creditId) {
        LetterCredit locked = creditRepository.findByIdForUpdate(creditId).orElseThrow();
        entityManager.refresh(locked);
        return locked;
    }

    private AcceptanceView toView(Acceptance a) {
        return new AcceptanceView(a.getId(), a.getAcceptanceNo(),
                a.getCredit().getCreditNo(), a.getPresentation().getPresentationNo(),
                a.getReviewVersion().getVersionNo(), a.getCreditVersionNo(), a.getAmount(),
                a.getCurrency(), a.getAcceptedBy(), a.getAcceptedAt(), a.getStatus(),
                a.getCreditVersionAtAcceptance(), a.getReversedBy(),
                a.getReversalReason(), a.getReversedAt());
    }
}
