package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.CreditVersion;
import com.chris64233.lettercredit.domain.LetterCredit;
import com.chris64233.lettercredit.exception.BusinessException;
import com.chris64233.lettercredit.exception.ErrorCode;
import com.chris64233.lettercredit.repository.AcceptanceRepository;
import com.chris64233.lettercredit.repository.BalanceMovementRepository;
import com.chris64233.lettercredit.repository.CreditVersionRepository;
import com.chris64233.lettercredit.repository.LetterCreditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class CreditService {

    private final LetterCreditRepository creditRepository;
    private final CreditVersionRepository versionRepository;
    private final AcceptanceRepository acceptanceRepository;
    private final BalanceMovementRepository movementRepository;

    public CreditService(LetterCreditRepository creditRepository,
                         CreditVersionRepository versionRepository,
                         AcceptanceRepository acceptanceRepository,
                         BalanceMovementRepository movementRepository) {
        this.creditRepository = creditRepository;
        this.versionRepository = versionRepository;
        this.acceptanceRepository = acceptanceRepository;
        this.movementRepository = movementRepository;
    }

    @Transactional
    public LetterCredit create(String creditNo,
                               String beneficiary,
                               String currency,
                               BigDecimal maxAmount,
                               LocalDate expiryDate,
                               List<String> allowedDocumentTypes) {
        LetterCredit credit = new LetterCredit(creditNo, beneficiary, currency.toUpperCase(),
                maxAmount, expiryDate, allowedDocumentTypes);
        credit = creditRepository.save(credit);
        // 开证即固化版本 1，后续修订只能新增版本，不能改写本版本。
        versionRepository.save(new CreditVersion(credit, credit.getCurrentVersionNo(),
                maxAmount, expiryDate, allowedDocumentTypes, null));
        return credit;
    }

    @Transactional(readOnly = true)
    public LetterCredit getByNo(String creditNo) {
        return creditRepository.findByCreditNo(creditNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREDIT_NOT_FOUND,
                        "信用证不存在: " + creditNo));
    }

    /**
     * 信用证余额查询。
     *
     * <p>{@code acceptedAmount} 为<strong>全部版本</strong>未撤销承兑的累计；
     * {@code availableAmount} 为当前尚可承兑的额度，= 当前版本最高金额
     * − 全版本承兑累计（统一金额信封，不为负）。各版本的承兑归属详见
     * {@link #versions(String)}。</p>
     */
    @Transactional(readOnly = true)
    public CreditBalanceView balance(String creditNo) {
        LetterCredit credit = getByNo(creditNo);
        return new CreditBalanceView(credit.getCreditNo(), credit.getBeneficiary(),
                credit.getCurrency(), credit.getMaxAmount(), credit.getAcceptedAmount(),
                credit.availableAmount(), credit.getExpiryDate(),
                List.copyOf(credit.getAllowedDocumentTypes()),
                credit.getCurrentVersionNo(), credit.getVersion());
    }

    /**
     * 信用证全部版本查询（版本差异对比依据），并附归属各版本的承兑累计。
     */
    @Transactional(readOnly = true)
    public List<CreditVersionView> versions(String creditNo) {
        LetterCredit credit = getByNo(creditNo);
        return versionRepository.findByCreditIdOrderByVersionNoAsc(credit.getId()).stream()
                .map(v -> new CreditVersionView(v.getVersionNo(), v.getStatus(),
                        v.getMaxAmount(), v.getExpiryDate(),
                        List.copyOf(v.getAllowedDocumentTypes()),
                        v.getSourceAmendmentNo(), v.getCreatedAt(),
                        acceptanceRepository
                                .sumAcceptedByCreditVersion(credit.getId(), v.getVersionNo())))
                .toList();
    }

    /**
     * 余额变化流水：承兑占用、撤销恢复与修订生效结转，按发生顺序排列。
     */
    @Transactional(readOnly = true)
    public List<BalanceMovementView> movements(String creditNo) {
        LetterCredit credit = getByNo(creditNo);
        return movementRepository.findByCreditIdOrderByOccurredAtAscIdAsc(credit.getId())
                .stream()
                .map(m -> new BalanceMovementView(m.getId(), m.getType().name(),
                        m.getAmountDelta(), m.getAcceptedAmountAfter(),
                        m.getMaxAmountAfter(), m.getCreditVersionNoAfter(),
                        m.getRefNo(), m.getOperator(), m.getOccurredAt()))
                .toList();
    }
}
