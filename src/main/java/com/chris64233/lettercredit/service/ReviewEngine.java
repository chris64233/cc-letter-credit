package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.CreditVersion;
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
 * <p>信用证允许单据类型既是允许的单据范围，也是规定必须提交的单据清单。
 * 差异规则：</p>
 * <ol>
 *   <li>{@link DiscrepancyCode#DOC_TYPE_NOT_ALLOWED}：出现清单外的单据类型（按类型去重）；</li>
 *   <li>{@link DiscrepancyCode#MISSING_REQUIRED_DOCUMENT}：缺少清单内规定的单据类型
 *       （可通过补交单据消除，产生无差异新版本）；</li>
 *   <li>{@link DiscrepancyCode#LC_EXPIRED}：交单日期晚于信用证有效期；</li>
 *   <li>{@link DiscrepancyCode#AMOUNT_EXCEEDS_BALANCE}：交单金额超过可用余额。</li>
 * </ol>
 *
 * <p>修订生效后存在多个信用证版本：条款（有效期、单据清单）按交单绑定的
 * {@link CreditVersion} 审核；可用余额为信用证级统一信封（当前最高金额 −
 * 全版本未撤销承兑累计），由调用方实时计算后传入。</p>
 */
@Component
public class ReviewEngine {

    /** 按信用证当前条款审核（版本 1 场景）。 */
    public List<Discrepancy> review(LetterCredit credit,
                                    BigDecimal amount,
                                    LocalDate presentationDate,
                                    List<DocumentSummary> documents) {
        return review(credit.availableAmount(), credit.getExpiryDate(),
                credit.getAllowedDocumentTypes(), amount, presentationDate, documents);
    }

    /**
     * 按交单绑定的信用证版本条款审核。
     *
     * @param version         交单所依据的信用证版本（提供有效期与单据清单）
     * @param availableAmount 信用证当前可用余额（统一信封，调用方实时计算）
     */
    public List<Discrepancy> review(CreditVersion version,
                                    BigDecimal availableAmount,
                                    BigDecimal amount,
                                    LocalDate presentationDate,
                                    List<DocumentSummary> documents) {
        return review(availableAmount, version.getExpiryDate(),
                version.getAllowedDocumentTypes(), amount, presentationDate, documents);
    }

    private List<Discrepancy> review(BigDecimal availableAmount,
                                     LocalDate expiryDate,
                                     List<String> allowedDocumentTypes,
                                     BigDecimal amount,
                                     LocalDate presentationDate,
                                     List<DocumentSummary> documents) {
        List<Discrepancy> discrepancies = new ArrayList<>();

        Set<String> presentedTypes = new LinkedHashSet<>();
        Set<String> rejectedTypes = new LinkedHashSet<>();
        for (DocumentSummary document : documents) {
            presentedTypes.add(document.getDocumentType());
            if (!allowedDocumentTypes.contains(document.getDocumentType())) {
                rejectedTypes.add(document.getDocumentType());
            }
        }
        for (String docType : rejectedTypes) {
            discrepancies.add(new Discrepancy(DiscrepancyCode.DOC_TYPE_NOT_ALLOWED, docType,
                    "单据类型 " + docType + " 不在信用证允许范围内"));
        }

        for (String requiredType : allowedDocumentTypes) {
            if (!presentedTypes.contains(requiredType)) {
                discrepancies.add(new Discrepancy(DiscrepancyCode.MISSING_REQUIRED_DOCUMENT,
                        requiredType, "缺少信用证规定提交的单据: " + requiredType));
            }
        }

        if (presentationDate.isAfter(expiryDate)) {
            discrepancies.add(new Discrepancy(DiscrepancyCode.LC_EXPIRED, null,
                    "交单日期 " + presentationDate + " 晚于信用证有效期 " + expiryDate));
        }

        if (availableAmount.compareTo(amount) < 0) {
            discrepancies.add(new Discrepancy(DiscrepancyCode.AMOUNT_EXCEEDS_BALANCE, null,
                    "交单金额 " + amount + " 超过信用证可用余额 " + availableAmount));
        }

        return List.copyOf(discrepancies);
    }
}
