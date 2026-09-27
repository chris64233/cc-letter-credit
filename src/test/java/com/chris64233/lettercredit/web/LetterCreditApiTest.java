package com.chris64233.lettercredit.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LetterCreditApiTest {

    @Autowired
    private MockMvc mockMvc;

    private String createLetterCredit(String maxAmount) throws Exception {
        String body = """
                {
                  "lcNumber": "%s",
                  "beneficiary": "受益人A",
                  "currency": "USD",
                  "maxAmount": %s,
                  "expiryDate": "2027-06-30",
                  "allowedDocumentTypes": ["INVOICE", "BOL"]
                }
                """.formatted("LC-" + UUID.randomUUID(), maxAmount);
        MvcResult result = mockMvc.perform(post("/api/letter-credits")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.availableAmount").value(Double.parseDouble(maxAmount)))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
    }

    private String submitPresentation(String lcId, String externalNo, String amount,
                                      String docsJson) throws Exception {
        String body = """
                {"externalPresentationNo": "%s", "amount": %s, "documents": %s, "reviewer": "审核员"}
                """.formatted(externalNo, amount, docsJson);
        MvcResult result = mockMvc.perform(post("/api/letter-credits/{lcId}/presentations", lcId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
    }

    @Test
    void fullCleanFlowOverHttp() throws Exception {
        String lcId = createLetterCredit("1000.00");
        String pId = submitPresentation(lcId, "EXT-API-1", "250.00",
                "[{\"docType\":\"INVOICE\",\"referenceNo\":\"INV-1\"}]");

        mockMvc.perform(get("/api/presentations/{id}/versions", pId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].result").value("CLEAN"))
                .andExpect(jsonPath("$[0].discrepancies", hasSize(0)));

        mockMvc.perform(post("/api/presentations/{id}/acceptances", pId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNo\": 1, \"operator\": \"承兑操作员\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(250.00))
                .andExpect(jsonPath("$.cancelled").value(false));

        mockMvc.perform(get("/api/letter-credits/{id}", lcId))
                .andExpect(jsonPath("$.availableAmount").value(750.00));

        // 台账查询
        MvcResult ledger = mockMvc.perform(get("/api/letter-credits/{id}/acceptances", lcId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andReturn();
        String acceptanceId = JsonPath.read(ledger.getResponse().getContentAsString(), "$[0].id").toString();

        // 撤销决定恢复余额
        mockMvc.perform(post("/api/acceptances/{id}/cancellations", acceptanceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"协商撤回\", \"operator\": \"撤销审批人\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").value("协商撤回"))
                .andExpect(jsonPath("$.operator").value("撤销审批人"));

        mockMvc.perform(get("/api/letter-credits/{id}", lcId))
                .andExpect(jsonPath("$.availableAmount").value(1000.00));
        mockMvc.perform(get("/api/letter-credits/{id}/acceptances", lcId))
                .andExpect(jsonPath("$[0].cancelled").value(true))
                .andExpect(jsonPath("$[0].cancellation.operator").value("撤销审批人"));
    }

    @Test
    void discrepantFlowOverHttp() throws Exception {
        String lcId = createLetterCredit("500.00");
        String pId = submitPresentation(lcId, "EXT-API-2", "100.00",
                "[{\"docType\":\"INVOICE\"},{\"docType\":\"CERT_OF_ORIGIN\"}]");

        mockMvc.perform(get("/api/presentations/{id}/versions", pId))
                .andExpect(jsonPath("$[0].result").value("DISCREPANT"))
                .andExpect(jsonPath("$[0].discrepancies")
                        .value(containsInAnyOrder("DOCUMENT_TYPE_NOT_ALLOWED:CERT_OF_ORIGIN")));

        // 未接受差异直接承兑 → 409
        mockMvc.perform(post("/api/presentations/{id}/acceptances", pId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNo\": 1, \"operator\": \"op\"}"))
                .andExpect(status().isConflict());

        // 接受范围不一致 → 409
        mockMvc.perform(post("/api/presentations/{id}/discrepancy-decisions", pId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNo\": 1, \"acceptedDiscrepancies\": [], \"decidedBy\": \"申请人\"}"))
                .andExpect(status().isConflict());

        // 完全一致 → 201，随后承兑成功
        mockMvc.perform(post("/api/presentations/{id}/discrepancy-decisions", pId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNo\": 1, \"acceptedDiscrepancies\": "
                                + "[\"DOCUMENT_TYPE_NOT_ALLOWED:CERT_OF_ORIGIN\"], \"decidedBy\": \"申请人\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/presentations/{id}/discrepancy-decisions", pId))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].decidedBy").value("申请人"));

        mockMvc.perform(post("/api/presentations/{id}/acceptances", pId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNo\": 1, \"operator\": \"op\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void supplementOverHttpAndStaleVersionRejected() throws Exception {
        String lcId = createLetterCredit("500.00");
        String pId = submitPresentation(lcId, "EXT-API-3", "100.00",
                "[{\"docType\":\"UNKNOWN\"}]");

        mockMvc.perform(post("/api/presentations/{id}/supplements", pId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documents\": [{\"docType\":\"INVOICE\"}], \"reviewer\": \"审核员\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.result").value("CLEAN"));

        // 基于旧版本承兑 → 409
        mockMvc.perform(post("/api/presentations/{id}/acceptances", pId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNo\": 1, \"operator\": \"op\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/presentations/{id}/acceptances", pId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"versionNo\": 2, \"operator\": \"op\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void unknownResourcesAndBadRequestsAreMapped() throws Exception {
        mockMvc.perform(get("/api/letter-credits/{id}", 999999))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/presentations/{id}", 999999))
                .andExpect(status().isNotFound());

        String lcId = createLetterCredit("100.00");
        // 无单据 → 400
        mockMvc.perform(post("/api/letter-credits/{lcId}/presentations", lcId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalPresentationNo\": \"EXT-API-4\", \"amount\": 10.00, "
                                + "\"documents\": [], \"reviewer\": \"r\"}"))
                .andExpect(status().isBadRequest());
    }
}
