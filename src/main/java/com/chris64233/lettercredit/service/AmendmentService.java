package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.AmendmentDecision;
import com.chris64233.lettercredit.domain.BalanceMovement;
import com.chris64233.lettercredit.domain.CreditAmendment;
import com.chris64233.lettercredit.domain.CreditVersion;
import com.chris64233.lettercredit.domain.LetterCredit;
import com.chris64233.lettercredit.domain.PendingPresentationPolicy;
import com.chris64233.lettercredit.domain.Presentation;
import com.chris64233.lettercredit.domain.PresentationStatus;
import com.chris64233.lettercredit.exception.BusinessException;
import com.chris64233.lettercredit.exception.ErrorCode;
import com.chris64233.lettercredit.repository.AmendmentDecisionRepository;
import com.chris64233.lettercredit.repository.BalanceMovementRepository;
import com.chris64233.lettercredit.repository.CreditAmendmentRepository;
import com.chris64233.lettercredit.repository.CreditVersionRepository;
import com.chris64233.lettercredit.repository.LetterCreditRepository;
import com.chris64233.lettercredit.repository.PresentationRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 信用证修订服务。
 *
 * <p>修订生命周期：提出（PROPOSED，冻结基础版本号与剩余金额）→ 受益人接受
 * （生成新信用证版本并处置既有未承兑交单）或拒绝；申请人亦可在决定前取消。
 * 所有改变余额/版本的事务都先锁定信用证行，与承兑、撤销事务使用相同的
 * “交单行 → 信用证行”加锁次序，串行化后在锁内重新校验，杜绝基于旧余额
 * 快照的累计超额。</p>
 */
@Service
public class AmendmentService {

    private final CreditAmendmentRepository amendmentRepository;
    private final AmendmentDecisionRepository decisionRepository;
    private final CreditVersionRepository versionRepository;
    private final LetterCreditRepository creditRepository;
    private final PresentationRepository presentationRepository;
    private final BalanceMovementRepository movementRepository;

    public AmendmentService(CreditAmendmentRepository amendmentRepository,
                            AmendmentDecisionRepository decisionRepository,
                            CreditVersionRepository versionRepository,
                            LetterCreditRepository creditRepository,
                            PresentationRepository presentationRepository,
                            BalanceMovementRepository movementRepository) {
        this.amendmentRepository = amendmentRepository;
        this.decisionRepository = decisionRepository;
        this.versionRepository = versionRepository;
        this.creditRepository = creditRepository;
        this.presentationRepository = presentationRepository;
        this.movementRepository = movementRepository;
    }

    /**
     * 申请人提出修订。
     *
     * @param amendmentNo 修订号（创建幂等键）
     * @param pendingPolicy 受影响未承兑交单的处置；字段确有变化且存在未承兑交单时必填
     */
    @Transactional
    public AmendmentView propose(String amendmentNo,
                                 String creditNo,
                                 BigDecimal proposedMaxAmount,
                                 LocalDate proposedExpiryDate,
                                 List<String> proposedDocTypes,
                                 PendingPresentationPolicy pendingPolicy,
                                 String proposedBy) {
        LetterCredit credit = lockCreditByNo(creditNo);

        // 修订号幂等：同号重复提交，内容一致则原样返回，不一致按冲突处理。
        Optional<CreditAmendment> sameNo = amendmentRepository.findByAmendmentNo(amendmentNo);
        if (sameNo.isPresent()) {
            CreditAmendment existing = sameNo.get();
            if (!existing.getCredit().getId().equals(credit.getId())
                    || !existing.targetMatches(proposedMaxAmount, proposedExpiryDate,
                            proposedDocTypes)
                    || existing.getPendingPolicy() != pendingPolicy) {
                throw new BusinessException(ErrorCode.AMENDMENT_DECISION_CONFLICT,
                        "修订号 " + amendmentNo + " 已存在且申请内容不一致");
            }
            return toView(existing);
        }

        if (amendmentRepository.existsByCreditIdAndStatus(credit.getId(),
                com.chris64233.lettercredit.domain.AmendmentStatus.PROPOSED)) {
            throw new BusinessException(ErrorCode.ACTIVE_AMENDMENT_EXISTS,
                    "信用证 " + creditNo + " 已存在一笔活动修订，须先结束该修订");
        }

        List<String> targetDocTypes = proposedDocTypes.stream().sorted().toList();
        List<String> affectedFields = affectedFields(credit, proposedMaxAmount,
                proposedExpiryDate, targetDocTypes);
        if (affectedFields.isEmpty()) {
            throw new BusinessException(ErrorCode.AMENDMENT_NO_CHANGE,
                    "修订内容与信用证当前条款完全一致，无任何字段变化");
        }

        List<String> pendingNos = pendingPresentationNos(credit, credit.getCurrentVersionNo());
        boolean affectsPending = !affectedFields.isEmpty() && !pendingNos.isEmpty();
        if (affectsPending && pendingPolicy == null) {
            throw new BusinessException(ErrorCode.PENDING_PRESENTATION_POLICY_REQUIRED,
                    "修订改变了 " + pendingNos.size()
                            + " 笔尚未承兑交单所依据的字段，必须明确其继续使用旧版本"
                            + "（KEEP_OLD_VERSION）或撤回后按新版本补交"
                            + "（WITHDRAW_AND_RESUBMIT）");
        }
        if (!affectsPending && pendingPolicy != null) {
            throw new BusinessException(ErrorCode.PENDING_PRESENTATION_POLICY_NOT_APPLICABLE,
                    "本次修订不影响任何既有未承兑交单，不应指定交单处置方式");
        }

        BigDecimal accepted = credit.getAcceptedAmount();
        BigDecimal remaining = credit.availableAmount();

        // 降额即时校验（以提出时点余额快照）：不得低于已承兑累计；
        // 选择保留旧版本时，还须为全部尚未处理的交单留出明确额度归属。
        validateAmountReduction(credit, proposedMaxAmount, affectedFields,
                pendingNos, pendingPolicy);

        CreditAmendment amendment = new CreditAmendment(amendmentNo, credit,
                credit.getCurrentVersionNo(), accepted, remaining,
                proposedMaxAmount, proposedExpiryDate, targetDocTypes,
                affectedFields, pendingNos, pendingPolicy, proposedBy);
        amendment = amendmentRepository.save(amendment);
        amendmentRepository.flush();
        return toView(amendment);
    }

    /**
     * 受益人决定（接受并生效 / 拒绝）。{@code eventNo} 为决定事件幂等键。
     */
    @Transactional
    public AmendmentView decide(String amendmentNo,
                                String eventNo,
                                boolean accepted,
                                BigDecimal targetMaxAmount,
                                LocalDate targetExpiryDate,
                                List<String> targetDocTypes,
                                List<String> acceptedFields,
                                String decidedBy,
                                String reason) {
        // 决定事件号幂等：同号重复提交直接返回既有决定；
        // 同事件号却指向另一笔修订 -> 冲突，防止事件串单。
        Optional<AmendmentDecision> sameEvent = decisionRepository.findByEventNo(eventNo);
        if (sameEvent.isPresent()) {
            AmendmentDecision existing = sameEvent.get();
            if (!existing.getAmendment().getAmendmentNo().equals(amendmentNo)) {
                throw new BusinessException(ErrorCode.AMENDMENT_DECISION_CONFLICT,
                        "决定事件号 " + eventNo + " 已用于另一笔修订 "
                                + existing.getAmendment().getAmendmentNo());
            }
            return toView(existing.getAmendment());
        }

        CreditAmendment amendment = lockAmendmentByNo(amendmentNo);

        // 固定加锁顺序：修订行 → 受影响交单行（按 id 升序）→ 信用证行，
        // 与承兑事务“交单行 → 信用证行”的顺序一致，杜绝死锁。
        List<Presentation> lockedPending = List.of();
        if (accepted && amendment.affectsPendingPresentations()
                && amendment.getPendingPolicy() == PendingPresentationPolicy.WITHDRAW_AND_RESUBMIT) {
            lockedPending = lockPendingPresentations(amendment);
        }
        LetterCredit credit = lockCredit(amendment.getCredit().getId());

        if (!amendment.isActive()) {
            throw new BusinessException(ErrorCode.AMENDMENT_NOT_ACTIVE,
                    "修订 " + amendmentNo + " 已处于终态 " + amendment.getStatus()
                            + "，不能再登记决定");
        }
        decisionRepository.findByAmendmentId(amendment.getId()).ifPresent(d -> {
            throw new BusinessException(ErrorCode.AMENDMENT_DECISION_CONFLICT,
                    "修订 " + amendmentNo + " 已存在受益人决定");
        });

        AmendmentDecision decision;
        if (accepted) {
            // 接受范围必须与当前修订版本完全一致：三项内容 + 受影响字段集合。
            List<String> docTypes = targetDocTypes == null ? List.of()
                    : targetDocTypes.stream().sorted().toList();
            if (!amendment.targetMatches(targetMaxAmount, targetExpiryDate, docTypes)
                    || !amendment.affectedFieldSet().equals(
                            new TreeSet<>(acceptedFields == null ? List.of() : acceptedFields))) {
                throw new BusinessException(ErrorCode.AMENDMENT_SCOPE_MISMATCH,
                        "接受范围与当前修订版本不完全一致，拒绝生效");
            }

            // 锁内基于当前余额与交单状态重新校验，防止修订提出后的承兑/撤销
            // 使旧快照失效。
            List<Presentation> livePending = livePendingPresentations(amendment, credit,
                    lockedPending);
            PendingPresentationPolicy policy = amendment.getPendingPolicy();
            validateAmountReduction(credit, targetMaxAmount,
                    amendment.getAffectedFields(),
                    livePending.stream().map(Presentation::getPresentationNo).toList(),
                    amendment.affectsPendingPresentations() ? policy : null);

            decision = new AmendmentDecision(eventNo, amendment, targetMaxAmount,
                    targetExpiryDate, docTypes, List.copyOf(amendment.affectedFieldSet()),
                    decidedBy);
            decisionRepository.save(decision);

            effectAmendment(amendment, credit, livePending, decidedBy);
        } else {
            decision = new AmendmentDecision(eventNo, amendment, decidedBy, reason);
            decisionRepository.save(decision);
            amendment.markRejected();
            amendmentRepository.flush();
        }
        return toView(amendment);
    }

    /**
     * 申请人在受益人决定前取消修订。申请保留，信用证版本不变。
     */
    @Transactional
    public AmendmentView cancel(String amendmentNo, String cancelledBy, String reason) {
        CreditAmendment amendment = lockAmendmentByNo(amendmentNo);
        lockCredit(amendment.getCredit().getId());
        if (!amendment.isActive()) {
            throw new BusinessException(ErrorCode.AMENDMENT_NOT_ACTIVE,
                    "修订 " + amendmentNo + " 已处于终态 " + amendment.getStatus()
                            + "，不能取消");
        }
        amendment.cancel(cancelledBy, reason);
        amendmentRepository.flush();
        return toView(amendment);
    }

    @Transactional(readOnly = true)
    public AmendmentView getByNo(String amendmentNo) {
        return toView(amendmentRepository.findByAmendmentNo(amendmentNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.AMENDMENT_NOT_FOUND,
                        "修订不存在: " + amendmentNo)));
    }

    @Transactional(readOnly = true)
    public List<AmendmentView> listByCredit(String creditNo) {
        LetterCredit credit = creditRepository.findByCreditNo(creditNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREDIT_NOT_FOUND,
                        "信用证不存在: " + creditNo));
        return amendmentRepository.findByCreditIdOrderByProposedAtAsc(credit.getId())
                .stream().map(this::toView).toList();
    }

    // ---- 内部逻辑 ----

    /**
     * 生效：旧版本置 SUPERSEDED → 生成新版本 → 更新信用证当前条款 →
     * 按策略处置未承兑交单 → 追加余额结转流水。既有承兑不做任何修改。
     */
    private void effectAmendment(CreditAmendment amendment,
                                 LetterCredit credit,
                                 List<Presentation> livePending,
                                 String operator) {
        try {
            CreditVersion current = versionRepository.findCurrentForUpdate(credit.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.VERSION_NOT_FOUND,
                            "信用证当前版本缺失"));
            if (current.getVersionNo() != amendment.getBaseVersionNo()) {
                // 活动修订唯一 + 行锁使该分支理论上不可达，仍作防御。
                throw new BusinessException(ErrorCode.CREDIT_VERSION_STALE,
                        "修订基础版本与当前版本不一致，拒绝生效");
            }

            int newVersionNo = current.getVersionNo() + 1;
            current.markSuperseded();
            versionRepository.save(new CreditVersion(credit, newVersionNo,
                    amendment.getProposedMaxAmount(), amendment.getProposedExpiryDate(),
                    amendment.getProposedAllowedDocumentTypes(),
                    amendment.getAmendmentNo()));

            credit.applyAmendment(amendment.getProposedMaxAmount(),
                    amendment.getProposedExpiryDate(),
                    amendment.getProposedAllowedDocumentTypes(), newVersionNo);

            if (amendment.getPendingPolicy() == PendingPresentationPolicy.WITHDRAW_AND_RESUBMIT) {
                for (Presentation pending : livePending) {
                    // 交单行已在锁内重新读取，置撤回终态；不释放任何额度（未承兑）。
                    pending.markWithdrawn();
                    presentationRepository.save(pending);
                }
            }

            amendment.markAccepted();

            // 余额结转流水：占用金额不变，记录版本切换与最高金额变化。
            movementRepository.save(new BalanceMovement(credit,
                    BalanceMovement.Type.AMENDMENT_EFFECT, BigDecimal.ZERO,
                    credit.getAcceptedAmount(), credit.getMaxAmount(),
                    newVersionNo, amendment.getAmendmentNo(), operator));

            amendmentRepository.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(ErrorCode.CREDIT_VERSION_STALE,
                    "信用证余额/版本并发变化，修订生效失败，请重新查询后处理");
        }
    }

    /**
     * 降额校验：
     * <ul>
     *   <li>新最高金额 &lt; 已承兑累计 → 拒绝（既有承兑不可修改）；</li>
     *   <li>受影响的未承兑交单选择继续使用旧版本时，新最高金额还须 ≥
     *       已承兑累计 + 这些交单金额，保证每笔未处理交单都有明确额度归属；</li>
     *   <li>选择撤回补交时，这些交单不再占用额度，无需计入。</li>
     * </ul>
     */
    private void validateAmountReduction(LetterCredit credit,
                                         BigDecimal proposedMaxAmount,
                                         List<String> affectedFields,
                                         List<String> keptPendingNos,
                                         PendingPresentationPolicy policy) {
        if (!affectedFields.contains(CreditAmendment.FIELD_MAX_AMOUNT)
                || proposedMaxAmount.compareTo(credit.getMaxAmount()) >= 0) {
            return;
        }
        BigDecimal floor = credit.getAcceptedAmount();
        if (policy == PendingPresentationPolicy.KEEP_OLD_VERSION) {
            for (String no : keptPendingNos) {
                Presentation p = presentationRepository.findByPresentationNo(no).orElse(null);
                if (p != null && p.getStatus() == PresentationStatus.PRESENTED) {
                    floor = floor.add(p.getAmount());
                }
            }
        }
        if (proposedMaxAmount.compareTo(floor) < 0) {
            throw new BusinessException(ErrorCode.AMENDMENT_AMOUNT_BELOW_ACCEPTED,
                    "修订后最高金额 " + proposedMaxAmount
                            + " 低于必须覆盖的金额下限 " + floor
                            + "（已承兑累计" + (policy == PendingPresentationPolicy.KEEP_OLD_VERSION
                            ? " + 保留旧版本的未承兑交单金额" : "") + "）");
        }
    }

    private List<Presentation> livePendingPresentations(CreditAmendment amendment,
                                                        LetterCredit credit,
                                                        List<Presentation> locked) {
        if (!amendment.affectsPendingPresentations()) {
            return List.of();
        }
        // 已加悲观锁的直接复用，避免重复查询；其余按号补查。
        List<Presentation> result = new ArrayList<>();
        for (Presentation p : locked) {
            if (p.getStatus() == PresentationStatus.PRESENTED
                    && p.getCreditVersionNo() == credit.getCurrentVersionNo()) {
                result.add(p);
            }
        }
        java.util.Set<String> lockedNos = locked.stream()
                .map(Presentation::getPresentationNo).collect(java.util.stream.Collectors.toSet());
        for (String no : amendment.getPendingPresentationNos()) {
            if (lockedNos.contains(no)) {
                continue;
            }
            presentationRepository.findByPresentationNo(no)
                    .filter(p -> p.getStatus() == PresentationStatus.PRESENTED
                            && p.getCreditVersionNo() == credit.getCurrentVersionNo())
                    .ifPresent(result::add);
        }
        return result;
    }

    /**
     * 按交单 id 升序锁定修订快照中的未承兑交单行（防死锁的固定次序）。
     * 只锁当前仍属基础版本的 PRESENTED 交单。
     */
    private List<Presentation> lockPendingPresentations(CreditAmendment amendment) {
        List<Presentation> toLock = new ArrayList<>();
        for (String no : amendment.getPendingPresentationNos()) {
            presentationRepository.findByPresentationNo(no)
                    .filter(p -> p.getStatus() == PresentationStatus.PRESENTED
                            && p.getCreditVersionNo() == amendment.getBaseVersionNo())
                    .ifPresent(toLock::add);
        }
        toLock.sort(java.util.Comparator.comparing(Presentation::getId));
        List<Presentation> locked = new ArrayList<>();
        for (Presentation p : toLock) {
            locked.add(presentationRepository.findByIdForUpdate(p.getId()).orElseThrow());
        }
        return locked;
    }

    private List<String> pendingPresentationNos(LetterCredit credit, int versionNo) {
        return presentationRepository
                .findByCreditIdAndCreditVersionNoAndStatus(credit.getId(), versionNo,
                        PresentationStatus.PRESENTED)
                .stream().map(Presentation::getPresentationNo).toList();
    }

    private List<String> affectedFields(LetterCredit credit,
                                        BigDecimal proposedMaxAmount,
                                        LocalDate proposedExpiryDate,
                                        List<String> proposedDocTypes) {
        SortedSet<String> fields = new TreeSet<>();
        if (proposedMaxAmount.compareTo(credit.getMaxAmount()) != 0) {
            fields.add(CreditAmendment.FIELD_MAX_AMOUNT);
        }
        if (!proposedExpiryDate.equals(credit.getExpiryDate())) {
            fields.add(CreditAmendment.FIELD_EXPIRY_DATE);
        }
        if (!new TreeSet<>(credit.getAllowedDocumentTypes()).equals(
                new TreeSet<>(proposedDocTypes))) {
            fields.add(CreditAmendment.FIELD_ALLOWED_DOCUMENT_TYPES);
        }
        return List.copyOf(fields);
    }

    private LetterCredit lockCreditByNo(String creditNo) {
        LetterCredit credit = creditRepository.findByCreditNo(creditNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREDIT_NOT_FOUND,
                        "信用证不存在: " + creditNo));
        return lockCredit(credit.getId());
    }

    private LetterCredit lockCredit(Long creditId) {
        return creditRepository.findByIdForUpdate(creditId).orElseThrow();
    }

    private CreditAmendment lockAmendmentByNo(String amendmentNo) {
        CreditAmendment amendment = amendmentRepository.findByAmendmentNo(amendmentNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.AMENDMENT_NOT_FOUND,
                        "修订不存在: " + amendmentNo));
        return amendmentRepository.findByIdForUpdate(amendment.getId()).orElseThrow();
    }

    private AmendmentView toView(CreditAmendment a) {
        Optional<AmendmentDecision> decision = decisionRepository
                .findByAmendmentId(a.getId());
        LetterCredit credit = a.getCredit();
        return new AmendmentView(a.getAmendmentNo(), credit.getCreditNo(), a.getStatus(),
                a.getBaseVersionNo(), a.getFrozenAcceptedAmount(), a.getFrozenRemainingAmount(),
                credit.getMaxAmount(), credit.getExpiryDate(),
                List.copyOf(credit.getAllowedDocumentTypes()),
                a.getProposedMaxAmount(), a.getProposedExpiryDate(),
                List.copyOf(a.getProposedAllowedDocumentTypes()),
                List.copyOf(a.getAffectedFields()), a.getPendingPolicy(),
                List.copyOf(a.getPendingPresentationNos()),
                a.getProposedBy(), a.getProposedAt(),
                a.getCancelledBy(), a.getCancelledReason(), a.getCancelledAt(),
                decision.map(this::toDecisionView).orElse(null));
    }

    private AmendmentDecisionView toDecisionView(AmendmentDecision d) {
        return new AmendmentDecisionView(d.getId(), d.getEventNo(),
                d.getAmendment().getAmendmentNo(), d.isAccepted(),
                d.getTargetMaxAmount(), d.getTargetExpiryDate(),
                List.copyOf(d.getTargetAllowedDocumentTypes()),
                List.copyOf(d.getAcceptedFields()),
                d.getDecidedBy(), d.getReason(), d.getDecidedAt());
    }
}
