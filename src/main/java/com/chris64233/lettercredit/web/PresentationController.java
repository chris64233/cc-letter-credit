package com.chris64233.lettercredit.web;

import com.chris64233.lettercredit.service.DiscrepancyDecisionService;
import com.chris64233.lettercredit.service.DiscrepancyDecisionView;
import com.chris64233.lettercredit.service.PresentationService;
import com.chris64233.lettercredit.service.PresentationView;
import com.chris64233.lettercredit.service.ReviewVersionView;
import com.chris64233.lettercredit.web.dto.AcceptDiscrepanciesRequest;
import com.chris64233.lettercredit.web.dto.CreatePresentationRequest;
import com.chris64233.lettercredit.web.dto.SupplementRequest;
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
 * 交单、审核版本与差异决定接口。
 */
@RestController
@RequestMapping("/api/presentations")
public class PresentationController {

    private final PresentationService presentationService;
    private final DiscrepancyDecisionService decisionService;

    public PresentationController(PresentationService presentationService,
                                  DiscrepancyDecisionService decisionService) {
        this.presentationService = presentationService;
        this.decisionService = decisionService;
    }

    /** 首次交单，立即形成审核版本 1。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PresentationView present(@Valid @RequestBody CreatePresentationRequest request) {
        return presentationService.present(request.presentationNo(), request.creditNo(),
                request.amount(), request.presentationDate(),
                CreditController.toDocuments(request.documents()));
    }

    /** 交单详情：含全部审核版本，旧版本继续保留。 */
    @GetMapping("/{presentationNo}")
    public PresentationView get(@PathVariable String presentationNo) {
        return presentationService.getByNo(presentationNo);
    }

    /** 补交单据，产生新审核版本。 */
    @PostMapping("/{presentationNo}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public PresentationView supplement(@PathVariable String presentationNo,
                                       @Valid @RequestBody SupplementRequest request) {
        return presentationService.supplement(presentationNo,
                CreditController.toDocuments(request.documents()));
    }

    /** 交单各审核版本查询。 */
    @GetMapping("/{presentationNo}/versions")
    public List<ReviewVersionView> versions(@PathVariable String presentationNo) {
        return presentationService.getByNo(presentationNo).versions();
    }

    /** 申请人明确接受当前版本的全部指定差异。 */
    @PostMapping("/{presentationNo}/discrepancy-decisions")
    @ResponseStatus(HttpStatus.CREATED)
    public DiscrepancyDecisionView acceptDiscrepancies(
            @PathVariable String presentationNo,
            @Valid @RequestBody AcceptDiscrepanciesRequest request) {
        return decisionService.accept(presentationNo, request.acceptedKeys(),
                request.acceptedBy());
    }

    /** 差异决定查询。 */
    @GetMapping("/{presentationNo}/discrepancy-decisions")
    public List<DiscrepancyDecisionView> decisions(@PathVariable String presentationNo) {
        return decisionService.listByPresentation(presentationNo);
    }
}
