package com.chris64233.lettercredit.web;

import com.chris64233.lettercredit.domain.AcceptanceStatus;
import com.chris64233.lettercredit.domain.DocumentSummary;
import com.chris64233.lettercredit.exception.BusinessException;
import com.chris64233.lettercredit.exception.ErrorCode;
import com.chris64233.lettercredit.service.AcceptanceService;
import com.chris64233.lettercredit.service.AcceptanceView;
import com.chris64233.lettercredit.service.CreditBalanceView;
import com.chris64233.lettercredit.service.CreditService;
import com.chris64233.lettercredit.service.PresentationService;
import com.chris64233.lettercredit.service.PresentationView;
import com.chris64233.lettercredit.web.dto.CreateCreditRequest;
import com.chris64233.lettercredit.web.dto.DocumentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/credits")
public class CreditController {

    private final CreditService creditService;
    private final PresentationService presentationService;
    private final AcceptanceService acceptanceService;

    public CreditController(CreditService creditService,
                            PresentationService presentationService,
                            AcceptanceService acceptanceService) {
        this.creditService = creditService;
        this.presentationService = presentationService;
        this.acceptanceService = acceptanceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreditBalanceView create(@Valid @RequestBody CreateCreditRequest request) {
        var credit = creditService.create(request.creditNo(), request.beneficiary(),
                request.currency(), request.maxAmount(), request.expiryDate(),
                request.allowedDocumentTypes());
        return creditService.balance(credit.getCreditNo());
    }

    /** 信用证余额查询。 */
    @GetMapping("/{creditNo}/balance")
    public CreditBalanceView balance(@PathVariable String creditNo) {
        return creditService.balance(creditNo);
    }

    /** 信用证下交单列表（含全部审核版本）。 */
    @GetMapping("/{creditNo}/presentations")
    public List<PresentationView> presentations(@PathVariable String creditNo) {
        return presentationService.listByCredit(creditNo);
    }

    /** 承兑台账查询，可按状态过滤（ACCEPTED / REVERSED）。 */
    @GetMapping("/{creditNo}/acceptances")
    public List<AcceptanceView> ledger(@PathVariable String creditNo,
                                       @RequestParam(required = false) String status) {
        AcceptanceStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            try {
                statusFilter = AcceptanceStatus.valueOf(status.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST_PARAM,
                        "未知承兑状态过滤值: " + status);
            }
        }
        return acceptanceService.ledger(creditNo, statusFilter);
    }

    static DocumentSummary toDocument(DocumentRequest d) {
        return new DocumentSummary(d.documentType(), d.copies(), d.description());
    }

    static List<DocumentSummary> toDocuments(List<DocumentRequest> documents) {
        return documents.stream().map(CreditController::toDocument).toList();
    }
}
