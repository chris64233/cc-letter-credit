package com.chris64233.lettercredit.web;

import com.chris64233.lettercredit.domain.DocumentSummary;
import com.chris64233.lettercredit.service.PresentationService;
import com.chris64233.lettercredit.web.dto.AcceptRequest;
import com.chris64233.lettercredit.web.dto.AcceptanceResponse;
import com.chris64233.lettercredit.web.dto.DiscrepancyDecisionRequest;
import com.chris64233.lettercredit.web.dto.DiscrepancyDecisionResponse;
import com.chris64233.lettercredit.web.dto.DocumentSummaryDto;
import com.chris64233.lettercredit.web.dto.PresentationResponse;
import com.chris64233.lettercredit.web.dto.PresentationVersionResponse;
import com.chris64233.lettercredit.web.dto.SubmitPresentationRequest;
import com.chris64233.lettercredit.web.dto.SupplementRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PresentationController {

    private final PresentationService presentationService;

    public PresentationController(PresentationService presentationService) {
        this.presentationService = presentationService;
    }

    @PostMapping("/api/letter-credits/{letterCreditId}/presentations")
    @ResponseStatus(HttpStatus.CREATED)
    public PresentationResponse submit(@PathVariable long letterCreditId,
                                       @RequestBody SubmitPresentationRequest request) {
        return PresentationResponse.from(presentationService.submitPresentation(
                letterCreditId, request.externalPresentationNo(), request.amount(),
                toDocuments(request.documents()), request.reviewer()));
    }

    @GetMapping("/api/presentations/{id}")
    public PresentationResponse get(@PathVariable long id) {
        return PresentationResponse.from(presentationService.getPresentation(id));
    }

    /**
     * 交单版本查询：含每次审核的差异清单。
     */
    @GetMapping("/api/presentations/{id}/versions")
    public List<PresentationVersionResponse> listVersions(@PathVariable long id) {
        return presentationService.listVersions(id).stream()
                .map(PresentationVersionResponse::from).toList();
    }

    @PostMapping("/api/presentations/{id}/supplements")
    @ResponseStatus(HttpStatus.CREATED)
    public PresentationVersionResponse supplement(@PathVariable long id,
                                                  @RequestBody SupplementRequest request) {
        return PresentationVersionResponse.from(presentationService.supplementPresentation(
                id, toDocuments(request.documents()), request.reviewer()));
    }

    @PostMapping("/api/presentations/{id}/discrepancy-decisions")
    @ResponseStatus(HttpStatus.CREATED)
    public DiscrepancyDecisionResponse decide(@PathVariable long id,
                                              @RequestBody DiscrepancyDecisionRequest request) {
        return DiscrepancyDecisionResponse.from(presentationService.decideDiscrepancies(
                id, request.versionNo(), request.acceptedDiscrepancies(), request.decidedBy()));
    }

    /**
     * 差异决定查询。
     */
    @GetMapping("/api/presentations/{id}/discrepancy-decisions")
    public List<DiscrepancyDecisionResponse> listDecisions(@PathVariable long id) {
        return presentationService.listDecisions(id).stream()
                .map(DiscrepancyDecisionResponse::from).toList();
    }

    @PostMapping("/api/presentations/{id}/acceptances")
    @ResponseStatus(HttpStatus.CREATED)
    public AcceptanceResponse accept(@PathVariable long id,
                                     @RequestBody AcceptRequest request) {
        return AcceptanceResponse.from(
                presentationService.acceptPresentation(id, request.versionNo(), request.operator()),
                null);
    }

    private static List<DocumentSummary> toDocuments(List<DocumentSummaryDto> documents) {
        return documents == null ? List.of()
                : documents.stream().map(DocumentSummaryDto::toEntity).toList();
    }
}
