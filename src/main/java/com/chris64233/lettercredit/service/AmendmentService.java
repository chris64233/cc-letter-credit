package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.Amendment;
import com.chris64233.lettercredit.domain.AmendmentDecision;
import com.chris64233.lettercredit.domain.AmendmentField;
import com.chris64233.lettercredit.domain.AmendmentStatus;
import com.chris64233.lettercredit.domain.BalanceChange;
import com.chris64233.lettercredit.domain.BalanceChangeType;
import com.chris64233.lettercredit.domain.CreditVersion;
import com.chris64233.lettercredit.domain.LetterCredit;
import com.chris64233.lettercredit.domain.PendingPresentationPolicy;
import com.chris64233.lettercredit.domain.Presentation;
import com.chris64233.lettercredit.exception.BusinessException;
import com.chris64233.lettercredit.exception.ErrorCode;
import com.chris64233.lettercredit.repository.AmendmentDecisionRepository;
import com.chris64233.lettercredit.repository.AmendmentRepository;
import com.chris64233.lettercredit.repository.BalanceChangeRepository;
import com.chris64233.lettercredit.repository.LetterCreditRepository;
import com.chris64233.lettercredit.repository.PresentationRepository;
import jakarta.persistence.EntityManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 信用证修订服务。
 *
 * <p>修订由申请人提出、受益人决定：</p>
 * <ul>
 *   <li><strong>创建</strong>：锁定信用证行，校验同一信用证仅一笔活动修订、至少修改一项条款、
 *       降低后最高金额不低于累计已承兑且仍能覆盖全部未承兑交单金额；创建即冻结基线版本号
 *       与剩余金额快照。</li>
 *   <li><strong>受益人决定</strong>：决定事件号幂等；接受时声明的字段变化集合必须与修订
 *       相对基线的实际变化完全一致；存在未承兑交单时必须明确「继续旧版本」或「撤回重交」。
 *       生效在锁内以<strong>实时余额与当前版本重新校验</strong>，通过后追加不可变新版本，
 *       旧版本与既有承兑不变。</li>
 *   <li><strong>拒绝 / 取消</strong>：申请与决定原样保留，不改变当前信用证版本。</li>
 * </ul>
 *
 * <p>并发：所有余额相关路径统一加锁顺序「先相关交单行（id 升序）后信用证行」，
 * 修订生效与交单承兑/撤销经信用证行锁串行，并在锁内重算余额，杜绝基于旧余额快照
 * 的累计承兑超额；修订实体自身带乐观锁。</p>
 */
@Service
public class AmendmentService {

    private final AmendmentRepository amendmentRepository;
    private final AmendmentDecisionRepository decisionRepository;
    private final LetterCreditRepository creditRepository;
    private final PresentationRepository presentationRepository;
    private final BalanceChangeRepository balanceChangeRepository;
    private final EntityManager entityManager;

    public AmendmentService(AmendmentRepository amendmentRepository,
                            AmendmentDecisionRepository decisionRepository,
                            LetterCreditRepository creditRepository,
                            PresentationRepository presentationRepository,
                            BalanceChangeRepository balanceChangeRepository,
                            EntityManager entityManager) {
        this.amendmentRepository = amendmentRepository;
        this.decisionRepository = decisionRepository;
        this.creditRepository = creditRepository;
        this.presentationRepository = presentationRepository;
        this.balanceChangeRepository = balanceChangeRepository;
        this.entityManager = entityManager;
    }

    /**
     * 申请人提出修订。{@code newMaxAmount}/{@code newExpiryDate}/{@code newAllowedDocumentTypes}
     * 为 null 的字段沿用信用证当前版本条款；至少一项必须不同。
     */
    @Transactional
    public AmendmentView propose(String amendmentNo,
                                 String creditNo,
                                 BigDecimal newMaxAmount,
                                 LocalDate newExpiryDate,
                                 List<String> newAllowedDocumentTypes,
                                 String proposedBy) {
        LetterCredit credit = lockCreditByNo(creditNo);

        // 创建幂等：修订号重复且内容一致 -> 原样返回既有修订；内容不一致 -> 冲突。
        var existing = amendmentRepository.findByAmendmentNo(amendmentNo);
        if (existing.isPresent()) {
            Amendment a = existing.get();
            if (!a.getCredit().getId().equals(credit.getId()) || !sameProposedTerms(a,
                    newMaxAmount, newExpiryDate, newAllowedDocumentTypes, credit)) {
                throw new BusinessException(ErrorCode.DUPLICATE_AMENDMENT_NO,
                        "修订号已存在且内容不一致: " + amendmentNo);
            }
            return toView(a);
        }

        if (amendmentRepository.existsByCreditIdAndStatus(credit.getId(), AmendmentStatus.PROPOSED)) {
            throw new BusinessException(ErrorCode.ACTIVE_AMENDMENT_EXISTS,
                    "信用证 " + creditNo + " 已存在一笔活动修订，请先完成或取消");
        }

        CreditVersion current = credit.getCurrentVersion();
        BigDecimal targetMax = newMaxAmount != null ? newMaxAmount : current.getMaxAmount();
        LocalDate targetExpiry = newExpiryDate != null ? newExpiryDate : current.getExpiryDate();
        List<String> targetDocTypes = newAllowedDocumentTypes != null
                ? List.copyOf(newAllowedDocumentTypes) : current.getAllowedDocumentTypes();

        SortedSet<String> changedFields = diffTerms(current, targetMax, targetExpiry, targetDocTypes);
        if (changedFields.isEmpty()) {
            throw new BusinessException(ErrorCode.AMENDMENT_NO_TERMS_CHANGED,
                    "修订内容与信用证当前版本完全相同，至少须修改最高金额、有效期或允许单据类型之一");
        }

        List<Presentation> pending = pendingPresentations(credit.getId());
        BigDecimal accepted = credit.getAcceptedAmount();
        // 创建时的硬下限：降低后最高金额不得低于累计已承兑（承兑不可撤回，无法压缩）。
        // 未承兑交单的归属不由申请人在创建时决定，而由受益人在接受时明确
        // （继续旧版本须覆盖、撤回重交则无需覆盖），因此此处不卡死未承兑总额。
        if (targetMax.compareTo(accepted) < 0) {
            throw new BusinessException(ErrorCode.AMENDMENT_AMOUNT_BELOW_ACCEPTED,
                    "修订后最高金额 " + targetMax + " 不得低于累计已承兑金额 " + accepted);
        }

        int seq = amendmentRepository.findByCreditIdOrderByAmendmentSeqAsc(credit.getId()).size() + 1;
        Amendment amendment = new Amendment(amendmentNo, credit, seq, current.getVersionNo(),
                proposedBy, targetMax, targetExpiry, targetDocTypes, accepted,
                current.getMaxAmount().subtract(accepted), pending.size());
        amendmentRepository.save(amendment);
        return toView(amendment);
    }

    /**
     * 受益人登记决定（接受生效 / 拒绝留痕）。
     *
     * @param decisionEventNo 决定事件号（全局唯一，幂等键）
     * @param accepted true=接受并生效；false=拒绝
     * @param acceptedChangedFields 受益人确认的字段变化键；接受时须与修订实际变化完全一致
     * @param pendingPolicy 存在未承兑交单时的归属策略（KEEP_OLD_VERSION / WITHDRAW_RESUBMIT）
     */
    @Transactional
    public AmendmentView decide(String amendmentNo,
                                String decisionEventNo,
                                boolean accepted,
                                List<String> acceptedChangedFields,
                                PendingPresentationPolicy pendingPolicy,
                                String decidedBy,
                                String remark) {
        // 决定事件号幂等：同一事件号重复提交，返回其所属修订（内容不一致按冲突处理）。
        var byEvent = decisionRepository.findByDecisionEventNo(decisionEventNo);
        if (byEvent.isPresent()) {
            AmendmentDecision d = byEvent.get();
            if (!d.getAmendment().getAmendmentNo().equals(amendmentNo)
                    || d.isAccepted() != accepted) {
                throw new BusinessException(ErrorCode.AMENDMENT_DECISION_CONFLICT,
                        "决定事件号 " + decisionEventNo + " 已用于其他修订决定");
            }
            return toView(d.getAmendment());
        }

        Amendment amendment = lockAmendment(amendmentNo);
        // 锁内权威复核：并发的接受/拒绝/取消已终结该修订时直接失败。
        if (!amendment.isProposed()) {
            throw new BusinessException(ErrorCode.AMENDMENT_NOT_ACTIVE,
                    "修订 " + amendmentNo + " 当前状态 " + amendment.getStatus()
                            + "，不能再登记决定");
        }

        if (!accepted) {
            AmendmentDecision decision = new AmendmentDecision(decisionEventNo, amendment, false,
                    List.of(), null, 0, decidedBy, remark);
            decisionRepository.save(decision);
            amendment.markRejected();
            return toView(amendment);
        }

        // 接受：先锁全部未承兑交单行（id 升序），再锁信用证行；
        // 与承兑（先交单行后信用证行）、补交保持同一加锁顺序，防止死锁。
        Long creditId = amendment.getCredit().getId();
        List<Presentation> pending = lockPendingPresentations(creditId);
        LetterCredit credit = lockCredit(creditId);

        // 重新校验版本与实时余额，不使用修订创建时的快照。
        if (credit.getCurrentVersionNo() != amendment.getBaseVersionNo()) {
            throw new BusinessException(ErrorCode.AMENDMENT_BASE_VERSION_STALE,
                    "修订基线版本 " + amendment.getBaseVersionNo() + " 已过期，当前版本 "
                            + credit.getCurrentVersionNo() + "，请基于最新版本重新申请");
        }
        CreditVersion base = credit.getVersion(amendment.getBaseVersionNo());
        SortedSet<String> actualFields = amendment.changedFields(base);
        SortedSet<String> requestedFields = new TreeSet<>(
                acceptedChangedFields == null ? List.of() : acceptedChangedFields);
        if (!requestedFields.equals(actualFields)) {
            throw new BusinessException(ErrorCode.AMENDMENT_SCOPE_MISMATCH,
                    "接受的字段变化范围与修订不一致：确认 " + requestedFields + "，实际 "
                            + actualFields);
        }

        BigDecimal liveAccepted = credit.getAcceptedAmount();
        if (amendment.getNewMaxAmount().compareTo(liveAccepted) < 0) {
            throw new BusinessException(ErrorCode.AMENDMENT_AMOUNT_BELOW_ACCEPTED,
                    "修订后最高金额 " + amendment.getNewMaxAmount()
                            + " 低于当前累计已承兑 " + liveAccepted + "，修订不能生效");
        }
        PendingPresentationPolicy policy = pendingPolicy;
        if (pending.isEmpty()) {
            policy = null;
        } else if (policy == null) {
            throw new BusinessException(ErrorCode.PENDING_PRESENTATION_POLICY_REQUIRED,
                    "存在 " + pending.size() + " 笔尚未承兑交单，须明确其继续使用旧版本"
                            + "（KEEP_OLD_VERSION）或撤回后按新版本补交（WITHDRAW_RESUBMIT）");
        } else if (policy == PendingPresentationPolicy.KEEP_OLD_VERSION) {
            validateAmountCoverage(credit.getCreditNo(), amendment.getNewMaxAmount(),
                    liveAccepted, pending);
        }

        BigDecimal maxBefore = credit.getCurrentVersion().getMaxAmount();
        try {
            // 追加不可变新版本（以实时累计承兑冻结剩余快照），旧版本与承兑保持不变。
            credit.appendAmendedVersion(amendment.getNewMaxAmount(), amendment.getNewExpiryDate(),
                    amendment.getNewAllowedDocumentTypes(), amendment);
            if (policy == PendingPresentationPolicy.WITHDRAW_RESUBMIT) {
                pending.forEach(Presentation::withdraw);
            }
            AmendmentDecision decision = new AmendmentDecision(decisionEventNo, amendment, true,
                    List.copyOf(actualFields), policy, pending.size(), decidedBy, remark);
            decisionRepository.save(decision);
            amendment.markEffective();

            balanceChangeRepository.save(new BalanceChange(credit,
                    BalanceChangeType.AMENDMENT_EFFECTIVE, amendment.getAmendmentNo(),
                    credit.getCurrentVersionNo(), maxBefore, amendment.getNewMaxAmount(),
                    liveAccepted, liveAccepted));
            // 提前 flush：修订乐观锁/唯一约束冲突在本事务内显式暴露。
            amendmentRepository.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.AMENDMENT_NOT_ACTIVE,
                    "修订 " + amendmentNo + " 被并发处理，本次决定失败，请重试");
        }
        return toView(amendment);
    }

    /**
     * 申请人在受益人决定前取消修订。取消事件号幂等；取消后申请留痕，当前版本不变。
     */
    @Transactional
    public AmendmentView cancel(String amendmentNo, String cancelEventNo,
                                String cancelledBy, String reason) {
        Amendment amendment = lockAmendment(amendmentNo);

        if (amendment.getStatus() == AmendmentStatus.CANCELLED
                && cancelEventNo.equals(amendment.getCancelEventNo())) {
            return toView(amendment);
        }
        // 锁内权威复核：与并发决定/取消串行后状态已不是 PROPOSED 即拒绝。
        if (!amendment.isProposed()) {
            throw new BusinessException(ErrorCode.AMENDMENT_NOT_ACTIVE,
                    "修订 " + amendmentNo + " 当前状态 " + amendment.getStatus()
                            + "，不能取消");
        }
        amendment.markCancelled(cancelEventNo, cancelledBy, reason);
        return toView(amendment);
    }

    @Transactional(readOnly = true)
    public AmendmentView get(String amendmentNo) {
        return toView(getAmendment(amendmentNo));
    }

    @Transactional(readOnly = true)
    public List<AmendmentView> listByCredit(String creditNo) {
        LetterCredit credit = creditRepository.findByCreditNo(creditNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREDIT_NOT_FOUND,
                        "信用证不存在: " + creditNo));
        return amendmentRepository.findByCreditIdOrderByAmendmentSeqAsc(credit.getId())
                .stream().map(this::toView).toList();
    }

    /* ---- 内部辅助 ---- */

    private void validateAmountCoverage(String creditNo, BigDecimal targetMax,
                                        BigDecimal accepted, List<Presentation> pending) {
        if (targetMax.compareTo(accepted) < 0) {
            throw new BusinessException(ErrorCode.AMENDMENT_AMOUNT_BELOW_ACCEPTED,
                    "修订后最高金额 " + targetMax + " 不得低于累计已承兑金额 " + accepted);
        }
        BigDecimal pendingTotal = pending.stream()
                .map(Presentation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (targetMax.compareTo(accepted.add(pendingTotal)) < 0) {
            throw new BusinessException(ErrorCode.PENDING_PRESENTATIONS_NOT_COVERED,
                    "修订后最高金额 " + targetMax + " 无法覆盖累计已承兑 " + accepted
                            + " 与尚未处理交单金额合计 " + pendingTotal
                            + "，未处理交单将失去明确归属（信用证 " + creditNo + "）");
        }
    }

    private SortedSet<String> diffTerms(CreditVersion base, BigDecimal targetMax,
                                        LocalDate targetExpiry, List<String> targetDocTypes) {
        SortedSet<String> fields = new TreeSet<>();
        if (targetMax.compareTo(base.getMaxAmount()) != 0) {
            fields.add(AmendmentField.MAX_AMOUNT);
        }
        if (!targetExpiry.equals(base.getExpiryDate())) {
            fields.add(AmendmentField.EXPIRY_DATE);
        }
        if (!new TreeSet<>(targetDocTypes).equals(new TreeSet<>(base.getAllowedDocumentTypes()))) {
            fields.add(AmendmentField.ALLOWED_DOCUMENT_TYPES);
        }
        return fields;
    }

    private boolean sameProposedTerms(Amendment a, BigDecimal newMaxAmount,
                                      LocalDate newExpiryDate, List<String> newDocTypes,
                                      LetterCredit credit) {
        CreditVersion current = credit.getCurrentVersion();
        BigDecimal targetMax = newMaxAmount != null ? newMaxAmount : current.getMaxAmount();
        LocalDate targetExpiry = newExpiryDate != null ? newExpiryDate : current.getExpiryDate();
        List<String> targetDocTypes = newDocTypes != null ? newDocTypes : current.getAllowedDocumentTypes();
        return a.getNewMaxAmount().compareTo(targetMax) == 0
                && a.getNewExpiryDate().equals(targetExpiry)
                && new TreeSet<>(a.getNewAllowedDocumentTypes()).equals(new TreeSet<>(targetDocTypes));
    }

    private List<Presentation> pendingPresentations(Long creditId) {
        return presentationRepository.findByCreditIdOrderByPresentationDateAscIdAsc(creditId)
                .stream().filter(Presentation::isPending).toList();
    }

    /**
     * 按 id 升序对未承兑交单行加写锁，并在锁内重新过滤状态
     * （并发承兑可能已将其置为 ACCEPTED）。
     */
    private List<Presentation> lockPendingPresentations(Long creditId) {
        List<Long> pendingIds = presentationRepository
                .findByCreditIdOrderByPresentationDateAscIdAsc(creditId).stream()
                .filter(Presentation::isPending).map(Presentation::getId)
                .sorted().toList();
        return pendingIds.stream()
                .map(id -> {
                    Presentation locked =
                            presentationRepository.findByIdForUpdate(id).orElseThrow();
                    // 显式刷新：FOR UPDATE 命中缓存时只加锁不刷新，须读并发承兑后的最新状态。
                    entityManager.refresh(locked);
                    return locked;
                })
                .filter(Presentation::isPending).toList();
    }

    private LetterCredit lockCreditByNo(String creditNo) {
        LetterCredit credit = creditRepository.findByCreditNo(creditNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREDIT_NOT_FOUND,
                        "信用证不存在: " + creditNo));
        return lockCredit(credit.getId());
    }

    private LetterCredit lockCredit(Long id) {
        LetterCredit locked = creditRepository.findByIdForUpdate(id).orElseThrow();
        entityManager.refresh(locked);
        return locked;
    }

    private Amendment getAmendment(String amendmentNo) {
        return amendmentRepository.findByAmendmentNo(amendmentNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.AMENDMENT_NOT_FOUND,
                        "修订不存在: " + amendmentNo));
    }

    /** 锁定修订行并刷新，串行化同一修订的并发决定/取消，杜绝旧状态快照。 */
    private Amendment lockAmendment(String amendmentNo) {
        Amendment amendment = getAmendment(amendmentNo);
        Amendment locked = amendmentRepository.findByIdForUpdate(amendment.getId()).orElseThrow();
        entityManager.refresh(locked);
        return locked;
    }

    private AmendmentView toView(Amendment a) {
        CreditVersion base = a.getCredit().getVersion(a.getBaseVersionNo());
        AmendmentDecisionView decisionView = decisionRepository.findByAmendmentId(a.getId())
                .map(d -> new AmendmentDecisionView(d.getDecisionEventNo(),
                        a.getAmendmentNo(), d.isAccepted(), List.copyOf(d.getAcceptedChangedFields()),
                        d.getPendingPresentationPolicy(), d.getPendingPresentationsAtDecision(),
                        d.getDecidedBy(), d.getDecidedAt(), d.getRemark()))
                .orElse(null);
        return new AmendmentView(a.getAmendmentNo(), a.getCredit().getCreditNo(),
                a.getAmendmentSeq(), a.getStatus(), a.getBaseVersionNo(), a.getProposedBy(),
                a.getProposedAt(), a.getNewMaxAmount(), a.getNewExpiryDate(),
                List.copyOf(a.getNewAllowedDocumentTypes()), a.changedFields(base),
                a.getFrozenAcceptedAmount(), a.getFrozenAvailableAmount(),
                a.getFrozenPendingPresentations(), decisionView, a.getCancelledBy(),
                a.getCancelledAt(), a.getCancellationReason());
    }
}
