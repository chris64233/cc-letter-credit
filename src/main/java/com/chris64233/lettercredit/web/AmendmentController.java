package com.chris64233.lettercredit.web;

import com.chris64233.lettercredit.service.AmendmentService;
import com.chris64233.lettercredit.service.AmendmentView;
import com.chris64233.lettercredit.web.dto.AmendmentDecisionRequest;
import com.chris64233.lettercredit.web.dto.CancelAmendmentRequest;
import com.chris64233.lettercredit.web.dto.CreateAmendmentRequest;
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
 * 信用证修订接口：申请人提出/取消，受益人接受或拒绝，以及修订与决定查询。
 */
@RestController
@RequestMapping("/api/credits/{creditNo}/amendments")
public class AmendmentController {

    private final AmendmentService amendmentService;

    public AmendmentController(AmendmentService amendmentService) {
        this.amendmentService = amendmentService;
    }

    /** 申请人提出修订（同一信用证同时仅允许一笔活动修订），修订号幂等。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AmendmentView propose(@PathVariable String creditNo,
                                 @Valid @RequestBody CreateAmendmentRequest request) {
        return amendmentService.propose(request.amendmentNo(), creditNo, request.newMaxAmount(),
                request.newExpiryDate(), request.newAllowedDocumentTypes(), request.proposedBy());
    }

    /** 信用证下全部修订（含已生效/拒绝/取消，按修订序号排列）。 */
    @GetMapping
    public List<AmendmentView> list(@PathVariable String creditNo) {
        return amendmentService.listByCredit(creditNo);
    }

    /** 单笔修订查询（含冻结快照与受益人决定）。 */
    @GetMapping("/{amendmentNo}")
    public AmendmentView get(@PathVariable String creditNo,
                             @PathVariable String amendmentNo) {
        return amendmentService.get(amendmentNo);
    }

    /** 受益人决定：接受（生效并产生新版本）或拒绝（留痕不改版本），决定事件号幂等。 */
    @PostMapping("/{amendmentNo}/decision")
    public AmendmentView decide(@PathVariable String creditNo,
                                @PathVariable String amendmentNo,
                                @Valid @RequestBody AmendmentDecisionRequest request) {
        return amendmentService.decide(amendmentNo, request.decisionEventNo(),
                request.accepted(), request.acceptedChangedFields(),
                request.pendingPresentationPolicy(), request.decidedBy(), request.remark());
    }

    /** 申请人在受益人决定前取消修订，取消事件号幂等，当前信用证版本不变。 */
    @PostMapping("/{amendmentNo}/cancellation")
    public AmendmentView cancel(@PathVariable String creditNo,
                                @PathVariable String amendmentNo,
                                @Valid @RequestBody CancelAmendmentRequest request) {
        return amendmentService.cancel(amendmentNo, request.cancelEventNo(),
                request.cancelledBy(), request.reason());
    }
}
