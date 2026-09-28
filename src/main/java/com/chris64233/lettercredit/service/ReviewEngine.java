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
 * <p>条款类规则（单据类型范围、有效期）按交单所依据的<strong>信用证版本</strong>
 * {@link CreditVersion} 审核——修订生效不重审旧版本交单；余额类规则按信用证
 * <strong>实时可用余额</strong>（当前版本最高金额 − 实时累计已承兑）审核，
 * 保证承兑并发时不使用旧余额快照。</p>
 *
 * <p>{@link CreditVersion#getAllowedDocumentTypes()} 既是允许的单据范围，也是
 * 规定必须提交的单据清单。差异规则：</p>
 * <ol>
 *   <li>{@link DiscrepancyCode#DOC_TYPE_NOT_ALLOWED}：出现清单外的单据类型（按类型去重）；</li>
 *   <li>{@link DiscrepancyCode#MISSING_REQUIRED_DOCUMENT}：缺少清单内规定的单据类型
 *       （可通过补交单据消除，产生无差异新版本）；</li>
 *   <li>{@link DiscrepancyCode#LC_EXPIRED}：交单日期晚于所依据版本的有效期；</li>
 *   <li>{@link DiscrepancyCode#AMOUNT_EXCEEDS_BALANCE}：交单金额超过信用证实时可用余额。</li>
 * </ol>
 */
@Component
public class ReviewEngine {

    /**
     * 按交单绑定的信用证版本条款审核，余额取信用证实时值。
     */
    public List<Discrepancy> review(LetterCredit credit,
                                    CreditVersion creditVersion,
                                    BigDecimal amount,
                                    LocalDate presentationDate,
                                    List<DocumentSummary> documents) {
        List<Discrepancy> discrepancies = new ArrayList<>();

        Set<String> presentedTypes = new LinkedHashSet<>();
        Set<String> rejectedTypes = new LinkedHashSet<>();
        for (DocumentSummary document : documents) {
            presentedTypes.add(document.getDocumentType());
            if (!creditVersion.allows(document.getDocumentType())) {
                rejectedTypes.add(document.getDocumentType());
            }
        }
        for (String docType : rejectedTypes) {
            discrepancies.add(new Discrepancy(DiscrepancyCode.DOC_TYPE_NOT_ALLOWED, docType,
                    "单据类型 " + docType + " 不在信用证版本 "
                            + creditVersion.getVersionNo() + " 允许范围内"));
        }

        for (String requiredType : creditVersion.getAllowedDocumentTypes()) {
            if (!presentedTypes.contains(requiredType)) {
                discrepancies.add(new Discrepancy(DiscrepancyCode.MISSING_REQUIRED_DOCUMENT,
                        requiredType, "缺少信用证规定提交的单据: " + requiredType));
            }
        }

        if (creditVersion.isExpiredOn(presentationDate)) {
            discrepancies.add(new Discrepancy(DiscrepancyCode.LC_EXPIRED, null,
                    "交单日期 " + presentationDate + " 晚于信用证版本 "
                            + creditVersion.getVersionNo() + " 有效期 "
                            + creditVersion.getExpiryDate()));
        }

        if (credit.availableAmount().compareTo(amount) < 0) {
            discrepancies.add(new Discrepancy(DiscrepancyCode.AMOUNT_EXCEEDS_BALANCE, null,
                    "交单金额 " + amount + " 超过信用证可用余额 " + credit.availableAmount()));
        }

        return List.copyOf(discrepancies);
    }

    /** 便捷重载：按信用证当前版本条款审核（开证后未修订场景）。 */
    public List<Discrepancy> review(LetterCredit credit,
                                    BigDecimal amount,
                                    LocalDate presentationDate,
                                    List<DocumentSummary> documents) {
        return review(credit, credit.getCurrentVersion(), amount, presentationDate, documents);
    }
}
