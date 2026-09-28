package com.chris64233.lettercredit.service;

import com.chris64233.lettercredit.domain.AmendmentField;
import com.chris64233.lettercredit.domain.BalanceChange;
import com.chris64233.lettercredit.domain.CreditVersion;
import com.chris64233.lettercredit.domain.LetterCredit;
import com.chris64233.lettercredit.exception.BusinessException;
import com.chris64233.lettercredit.exception.ErrorCode;
import com.chris64233.lettercredit.repository.BalanceChangeRepository;
import com.chris64233.lettercredit.repository.CreditVersionRepository;
import com.chris64233.lettercredit.repository.LetterCreditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 信用证版本与余额变动查询服务（只读）。
 *
 * <p>提供信用证全部版本、指定版本、相邻/指定版本条款差异以及余额变动流水查询。
 * 版本与流水均只追加、不可修改。</p>
 */
@Service
public class CreditVersionService {

    private final LetterCreditRepository creditRepository;
    private final CreditVersionRepository versionRepository;
    private final BalanceChangeRepository balanceChangeRepository;

    public CreditVersionService(LetterCreditRepository creditRepository,
                                CreditVersionRepository versionRepository,
                                BalanceChangeRepository balanceChangeRepository) {
        this.creditRepository = creditRepository;
        this.versionRepository = versionRepository;
        this.balanceChangeRepository = balanceChangeRepository;
    }

    /** 列出信用证全部版本（旧版本永久保留）。 */
    @Transactional(readOnly = true)
    public List<CreditVersionView> listVersions(String creditNo) {
        LetterCredit credit = requireCredit(creditNo);
        return versionRepository.findByCreditIdOrderByVersionNoAsc(credit.getId())
                .stream().map(this::toView).toList();
    }

    /** 查询指定版本。 */
    @Transactional(readOnly = true)
    public CreditVersionView getVersion(String creditNo, int versionNo) {
        LetterCredit credit = requireCredit(creditNo);
        return versionRepository.findByCreditIdAndVersionNo(credit.getId(), versionNo)
                .map(this::toView)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERSION_NOT_FOUND,
                        "信用证 " + creditNo + " 不存在版本 " + versionNo));
    }

    /**
     * 版本差异查询。{@code toVersionNo} 为空时比较相邻两版本；
     * {@code fromVersionNo} 为空时从版本 1 起。
     */
    @Transactional(readOnly = true)
    public CreditVersionDiffView diff(String creditNo, Integer fromVersionNo, Integer toVersionNo) {
        LetterCredit credit = requireCredit(creditNo);
        List<CreditVersion> versions =
                versionRepository.findByCreditIdOrderByVersionNoAsc(credit.getId());
        if (versions.isEmpty()) {
            throw new BusinessException(ErrorCode.VERSION_NOT_FOUND,
                    "信用证 " + creditNo + " 尚无版本");
        }
        int fromNo = fromVersionNo != null ? fromVersionNo : versions.get(0).getVersionNo();
        int toNo = toVersionNo != null ? toVersionNo
                : Math.max(fromNo + 1, versions.get(versions.size() - 1).getVersionNo());
        CreditVersion from = findVersion(versions, creditNo, fromNo);
        CreditVersion to = findVersion(versions, creditNo, toNo);
        return diff(creditNo, from, to);
    }

    /** 当前版本相对其前一版本的差异（修订生效内容）。 */
    @Transactional(readOnly = true)
    public CreditVersionDiffView latestDiff(String creditNo) {
        LetterCredit credit = requireCredit(creditNo);
        List<CreditVersion> versions =
                versionRepository.findByCreditIdOrderByVersionNoAsc(credit.getId());
        CreditVersion to = versions.get(versions.size() - 1);
        CreditVersion from = versions.size() > 1 ? versions.get(versions.size() - 2) : to;
        return diff(creditNo, from, to);
    }

    /** 余额变动流水：承兑占用、撤销恢复、修订生效（最高金额变化）。 */
    @Transactional(readOnly = true)
    public List<BalanceChangeView> balanceChanges(String creditNo) {
        LetterCredit credit = requireCredit(creditNo);
        return balanceChangeRepository
                .findByCreditIdOrderByOccurredAtAscIdAsc(credit.getId())
                .stream().map(this::toView).toList();
    }

    private CreditVersionDiffView diff(String creditNo, CreditVersion from, CreditVersion to) {
        if (from.getVersionNo() >= to.getVersionNo()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST_PARAM,
                    "版本差异的起始版本须小于目标版本: " + from.getVersionNo()
                            + " -> " + to.getVersionNo() + "（信用证 " + creditNo + "）");
        }
        SortedSet<String> changed = new TreeSet<>();
        boolean amountChanged = from.getMaxAmount().compareTo(to.getMaxAmount()) != 0;
        boolean expiryChanged = !from.getExpiryDate().equals(to.getExpiryDate());
        boolean docsChanged = !new TreeSet<>(from.getAllowedDocumentTypes())
                .equals(new TreeSet<>(to.getAllowedDocumentTypes()));
        if (amountChanged) {
            changed.add(AmendmentField.MAX_AMOUNT);
        }
        if (expiryChanged) {
            changed.add(AmendmentField.EXPIRY_DATE);
        }
        if (docsChanged) {
            changed.add(AmendmentField.ALLOWED_DOCUMENT_TYPES);
        }
        return new CreditVersionDiffView(from.getVersionNo(), to.getVersionNo(), changed,
                amountChanged, from.getMaxAmount(), to.getMaxAmount(),
                expiryChanged, from.getExpiryDate(), to.getExpiryDate(),
                docsChanged, List.copyOf(from.getAllowedDocumentTypes()),
                List.copyOf(to.getAllowedDocumentTypes()));
    }

    private CreditVersion findVersion(List<CreditVersion> versions, String creditNo, int no) {
        return versions.stream().filter(v -> v.getVersionNo() == no).findAny()
                .orElseThrow(() -> new BusinessException(ErrorCode.VERSION_NOT_FOUND,
                        "信用证 " + creditNo + " 不存在版本 " + no));
    }

    private LetterCredit requireCredit(String creditNo) {
        return creditRepository.findByCreditNo(creditNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREDIT_NOT_FOUND,
                        "信用证不存在: " + creditNo));
    }

    private CreditVersionView toView(CreditVersion v) {
        return new CreditVersionView(v.getVersionNo(), v.getKind(), v.getMaxAmount(),
                v.getExpiryDate(), List.copyOf(v.getAllowedDocumentTypes()),
                v.getFrozenAcceptedAmount(), v.getFrozenAvailableAmount(), v.getEffectiveAt());
    }

    private BalanceChangeView toView(BalanceChange c) {
        return new BalanceChangeView(c.getRefNo(), c.getChangeType(), c.getCreditVersionNo(),
                c.getMaxAmountBefore(), c.getMaxAmountAfter(), c.getAcceptedBefore(),
                c.getAcceptedAfter(), c.getAvailableBefore(), c.getAvailableAfter(),
                c.getOccurredAt());
    }
}
