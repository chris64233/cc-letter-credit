package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.CreditVersion;
import com.chris64233.lettercredit.domain.LetterCredit;
import com.chris64233.lettercredit.exception.BusinessException;
import com.chris64233.lettercredit.exception.ErrorCode;
import com.chris64233.lettercredit.repository.LetterCreditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class CreditService {

    private final LetterCreditRepository creditRepository;

    public CreditService(LetterCreditRepository creditRepository) {
        this.creditRepository = creditRepository;
    }

    @Transactional
    public LetterCredit create(String creditNo,
                               String beneficiary,
                               String currency,
                               BigDecimal maxAmount,
                               LocalDate expiryDate,
                               List<String> allowedDocumentTypes) {
        LetterCredit credit = new LetterCredit(creditNo, beneficiary, currency.toUpperCase());
        credit.attachInitialVersion(maxAmount, expiryDate, allowedDocumentTypes);
        return creditRepository.save(credit);
    }

    @Transactional(readOnly = true)
    public LetterCredit getByNo(String creditNo) {
        return creditRepository.findByCreditNo(creditNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREDIT_NOT_FOUND,
                        "信用证不存在: " + creditNo));
    }

    /**
     * 信用证余额查询：当前版本最高金额、已承兑占用、可用余额、当前版本号与乐观锁版本。
     */
    @Transactional(readOnly = true)
    public CreditBalanceView balance(String creditNo) {
        LetterCredit credit = getByNo(creditNo);
        CreditVersion current = credit.getCurrentVersion();
        return new CreditBalanceView(credit.getCreditNo(), credit.getBeneficiary(),
                credit.getCurrency(), current.getMaxAmount(), credit.getAcceptedAmount(),
                credit.availableAmount(), current.getExpiryDate(),
                List.copyOf(current.getAllowedDocumentTypes()), current.getVersionNo(),
                credit.getVersion());
    }
}
