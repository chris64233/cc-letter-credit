package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.LetterCredit;
import com.chris64233.lettercredit.repo.LetterCreditRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * 信用证开立与余额查询。
 */
@Service
public class LetterCreditService {

    private final LetterCreditRepository letterCreditRepository;

    public LetterCreditService(LetterCreditRepository letterCreditRepository) {
        this.letterCreditRepository = letterCreditRepository;
    }

    @Transactional
    public LetterCredit createLetterCredit(String lcNumber, String beneficiary, String currency,
                                           BigDecimal maxAmount, LocalDate expiryDate,
                                           Set<String> allowedDocumentTypes) {
        if (lcNumber == null || lcNumber.isBlank()) {
            throw new BadRequestException("信用证编号不能为空");
        }
        if (beneficiary == null || beneficiary.isBlank()) {
            throw new BadRequestException("受益人不能为空");
        }
        if (currency == null || currency.isBlank()) {
            throw new BadRequestException("币种不能为空");
        }
        requireAmount(maxAmount, "最高金额");
        if (expiryDate == null) {
            throw new BadRequestException("有效期不能为空");
        }
        if (allowedDocumentTypes == null || allowedDocumentTypes.isEmpty()) {
            throw new BadRequestException("允许的单据类型不能为空");
        }
        try {
            return letterCreditRepository.saveAndFlush(new LetterCredit(
                    lcNumber, beneficiary, currency, maxAmount, expiryDate, allowedDocumentTypes));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("信用证编号已存在: " + lcNumber);
        }
    }

    @Transactional(readOnly = true)
    public LetterCredit getLetterCredit(long id) {
        return letterCreditRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("信用证不存在: " + id));
    }

    static void requireAmount(BigDecimal amount, String label) {
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException(label + "必须为正数");
        }
        if (amount.scale() > 2) {
            throw new BadRequestException(label + "最多保留两位小数");
        }
    }
}
