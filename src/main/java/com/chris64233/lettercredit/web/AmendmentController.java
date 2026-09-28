package com.chris64233.lettercredit.web;

import com.chris64233.lettercredit.service.AmendmentService;
import com.chris64233.lettercredit.service.AmendmentView;
import com.chris64233.lettercredit.web.dto.AmendmentDecisionRequest;
import com.chris64233.lettercredit.web.dto.CancelAmendmentRequest;
import com.chris64233.lettercredit.web.dto.ProposeAmendmentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 信用证修订接口：提出、受益人决定（接受生效/拒绝）、申请人取消与查询。
 */
@RestController
@RequestMapping("/api")
public class AmendmentController {

    private final AmendmentService amendmentService;

    public AmendmentController(AmendmentService amendmentService) {
        this.amendmentService = amendmentService;
    }

    /** 申请人提出修订（修订号幂等）。 */
    @PostMapping("/credits/{creditNo}/amendments")
    @ResponseStatus(HttpStatus.CREATED)
    public AmendmentView propose(@PathVariable String creditNo,
                                 @Valid @RequestBody ProposeAmendmentRequest request) {
        return amendmentService.propose(request.amendmentNo(), creditNo,
                request.proposedMaxAmount(), request.proposedExpiryDate(),
                request.proposedAllowedDocumentTypes(), request.pendingPolicy(),
                request.proposedBy());
    }

    /** 某信用证的全部修订（含已接受/拒绝/取消，申请与决定均保留）。 */
    @GetMapping("/credits/{creditNo}/amendments")
    public List<AmendmentView> list(@PathVariable String creditNo) {
        return amendmentService.listByCredit(creditNo);
    }

    /** 修订详情（含冻结快照与受益人决定）。 */
    @GetMapping("/amendments/{amendmentNo}")
    public AmendmentView get(@PathVariable String amendmentNo) {
        return amendmentService.getByNo(amendmentNo);
    }

    /** 受益人决定：接受（范围须与当前修订完全一致，随即生效）或拒绝；事件号幂等。 */
    @PostMapping("/amendments/{amendmentNo}/decisions")
    @ResponseStatus(HttpStatus.OK)
    public AmendmentView decide(@PathVariable String amendmentNo,
                                @Valid @RequestBody AmendmentDecisionRequest request) {
        return amendmentService.decide(amendmentNo, request.eventNo(), request.accepted(),
                request.targetMaxAmount(), request.targetExpiryDate(),
                request.targetAllowedDocumentTypes(), request.acceptedFields(),
                request.decidedBy(), request.reason());
    }

    /** 申请人在受益人决定前取消修订，版本不变。 */
    @PostMapping("/amendments/{amendmentNo}/cancel")
    @ResponseStatus(HttpStatus.OK)
    public AmendmentView cancel(@PathVariable String amendmentNo,
                                @Valid @RequestBody CancelAmendmentRequest request) {
        return amendmentService.cancel(amendmentNo, request.cancelledBy(), request.reason());
    }
}
