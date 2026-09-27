package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.Discrepancy;
import com.chris64233.lettercredit.domain.DiscrepancyCode;
import com.chris64233.lettercredit.domain.DocumentSummary;
import com.chris64233.lettercredit.domain.LetterCredit;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 交单审核规则引擎：对一次提交的全部单据出具不可修改的差异清单。
 *
 * <p>信用证 {@code allowedDocumentTypes} 既是允许的单据范围，也是规定
 * 必须提交的单据清单。差异规则：</p>
 * <ol>
 *   <li>{@link DiscrepancyCode#DOC_TYPE_NOT_ALLOWED}：出现清单外的单据类型（按类型去重）；</li>
 *   <li>{@link DiscrepancyCode#MISSING_REQUIRED_DOCUMENT}：缺少清单内规定的单据类型
 *       （可通过补交单据消除，产生无差异新版本）；</li>
 *   <li>{@link DiscrepancyCode#LC_EXPIRED}：交单日期晚于信用证有效期；</li>
 *   <li>{@link DiscrepancyCode#AMOUNT_EXCEEDS_BALANCE}：交单金额超过信用证当前可用余额。</li>
 * </ol>
 */
@Component
public class ReviewEngine {

    public List<Discrepancy> review(LetterCredit credit,
                                    BigDecimal amount,
                                    LocalDate presentationDate,
                                    List<DocumentSummary> documents) {
        List<Discrepancy> discrepancies = new ArrayList<>();

        Set<String> presentedTypes = new LinkedHashSet<>();
        Set<String> rejectedTypes = new LinkedHashSet<>();
        for (DocumentSummary document : documents) {
            presentedTypes.add(document.getDocumentType());
            if (!credit.allows(document.getDocumentType())) {
                rejectedTypes.add(document.getDocumentType());
            }
        }
        for (String docType : rejectedTypes) {
            discrepancies.add(new Discrepancy(DiscrepancyCode.DOC_TYPE_NOT_ALLOWED, docType,
                    "单据类型 " + docType + " 不在信用证允许范围内"));
        }

        for (String requiredType : credit.getAllowedDocumentTypes()) {
            if (!presentedTypes.contains(requiredType)) {
                discrepancies.add(new Discrepancy(DiscrepancyCode.MISSING_REQUIRED_DOCUMENT,
                        requiredType, "缺少信用证规定提交的单据: " + requiredType));
            }
        }

        if (credit.isExpiredOn(presentationDate)) {
            discrepancies.add(new Discrepancy(DiscrepancyCode.LC_EXPIRED, null,
                    "交单日期 " + presentationDate + " 晚于信用证有效期 " + credit.getExpiryDate()));
        }

        if (credit.availableAmount().compareTo(amount) < 0) {
            discrepancies.add(new Discrepancy(DiscrepancyCode.AMOUNT_EXCEEDS_BALANCE, null,
                    "交单金额 " + amount + " 超过信用证可用余额 " + credit.availableAmount()));
        }

        return List.copyOf(discrepancies);
    }
}
