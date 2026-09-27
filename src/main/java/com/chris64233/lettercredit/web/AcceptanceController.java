package com.chris64233.lettercredit.web;

import com.chris64233.lettercredit.service.AcceptanceService;
import com.chris64233.lettercredit.service.AcceptanceView;
import com.chris64233.lettercredit.web.dto.AcceptRequest;
import com.chris64233.lettercredit.web.dto.ReverseRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 承兑与独立撤销接口。
 */
@RestController
@RequestMapping("/api")
public class AcceptanceController {

    private final AcceptanceService acceptanceService;

    public AcceptanceController(AcceptanceService acceptanceService) {
        this.acceptanceService = acceptanceService;
    }

    /** 承兑（支持部分承兑），同一交单号幂等。 */
    @PostMapping("/presentations/{presentationNo}/acceptance")
    @ResponseStatus(HttpStatus.CREATED)
    public AcceptanceView accept(@PathVariable String presentationNo,
                                 @Valid @RequestBody AcceptRequest request) {
        return acceptanceService.accept(presentationNo, request.amount(),
                request.expectedReviewVersionNo(), request.expectedCreditVersion(),
                request.acceptedBy());
    }

    /** 按交单号查询承兑。 */
    @GetMapping("/presentations/{presentationNo}/acceptance")
    public AcceptanceView getByPresentation(@PathVariable String presentationNo) {
        return acceptanceService.getByPresentationNo(presentationNo);
    }

    /** 独立撤销决定：记录处理人与完整原因，同事务恢复余额。 */
    @PostMapping("/acceptances/{acceptanceNo}/reversal")
    @ResponseStatus(HttpStatus.OK)
    public AcceptanceView reverse(@PathVariable String acceptanceNo,
                                  @Valid @RequestBody ReverseRequest request) {
        return acceptanceService.reverse(acceptanceNo, request.reversedBy(), request.reason());
    }

    @GetMapping("/acceptances/{acceptanceNo}")
    public AcceptanceView get(@PathVariable String acceptanceNo) {
        return acceptanceService.getByNo(acceptanceNo);
    }
}
