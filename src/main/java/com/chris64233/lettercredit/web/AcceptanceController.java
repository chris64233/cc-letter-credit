package com.chris64233.lettercredit.web;

import com.chris64233.lettercredit.service.PresentationService;
import com.chris64233.lettercredit.web.dto.AcceptanceResponse;
import com.chris64233.lettercredit.web.dto.CancelRequest;
import com.chris64233.lettercredit.web.dto.CancellationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AcceptanceController {

    private final PresentationService presentationService;

    public AcceptanceController(PresentationService presentationService) {
        this.presentationService = presentationService;
    }

    /**
     * 承兑台账查询：信用证下全部承兑记录及其撤销决定。
     */
    @GetMapping("/api/letter-credits/{letterCreditId}/acceptances")
    public List<AcceptanceResponse> ledger(@PathVariable long letterCreditId) {
        return presentationService.listAcceptanceLedger(letterCreditId).stream()
                .map(e -> AcceptanceResponse.from(e.acceptance(), e.cancellation()))
                .toList();
    }

    /**
     * 撤销承兑：独立的撤销决定，恢复信用证余额。
     */
    @PostMapping("/api/acceptances/{id}/cancellations")
    @ResponseStatus(HttpStatus.CREATED)
    public CancellationResponse cancel(@PathVariable long id,
                                       @RequestBody CancelRequest request) {
        return CancellationResponse.from(presentationService.cancelAcceptance(
                id, request.reason(), request.operator()));
    }
}
