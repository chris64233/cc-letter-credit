package com.chris64233.lettercredit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 信用证修订流程 HTTP 接口测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AmendmentApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void amendmentAcceptanceFlowOverHttp() throws Exception {
        String creditNo = unique("LC");

        // 开证：版本 1，仅允许 INVOICE
        mockMvc.perform(post("/api/credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "creditNo", creditNo,
                                "beneficiary", "受益人修订",
                                "currency", "USD",
                                "maxAmount", new BigDecimal("1000.0000"),
                                "expiryDate", LocalDate.now().plusMonths(1).toString(),
                                "allowedDocumentTypes", List.of("INVOICE")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentVersionNo").value(1));

        // 一笔未承兑交单（受修订影响）
        String presentationNo = unique("P");
        mockMvc.perform(post("/api/presentations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "presentationNo", presentationNo,
                                "creditNo", creditNo,
                                "amount", new BigDecimal("200.0000"),
                                "presentationDate", LocalDate.now().toString(),
                                "documents", List.of(Map.of("documentType", "INVOICE", "copies", 1))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditVersionNo").value(1));

        // 提出修订（未给未承兑交单处置方式）-> 422
        String amendmentNo = unique("AMD");
        mockMvc.perform(post("/api/credits/" + creditNo + "/amendments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amendmentNo", amendmentNo,
                                "proposedMaxAmount", new BigDecimal("800.0000"),
                                "proposedExpiryDate", LocalDate.now().plusMonths(6).toString(),
                                "proposedAllowedDocumentTypes", List.of("INVOICE"),
                                "proposedBy", "applicant-1"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PENDING_PRESENTATION_POLICY_REQUIRED"));

        // 明确保留旧版本，重新提出成功（同修订号不能复用内容不一致的申请，换号）
        amendmentNo = unique("AMD");
        mockMvc.perform(post("/api/credits/" + creditNo + "/amendments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amendmentNo", amendmentNo,
                                "proposedMaxAmount", new BigDecimal("800.0000"),
                                "proposedExpiryDate", LocalDate.now().plusMonths(6).toString(),
                                "proposedAllowedDocumentTypes", List.of("INVOICE"),
                                "pendingPolicy", "KEEP_OLD_VERSION",
                                "proposedBy", "applicant-1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROPOSED"))
                .andExpect(jsonPath("$.baseVersionNo").value(1))
                .andExpect(jsonPath("$.pendingPresentationNos[0]").value(presentationNo))
                .andExpect(jsonPath("$.affectedFields[0]").exists());

        // 活动修订期间新交单被拒 -> 409
        mockMvc.perform(post("/api/presentations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "presentationNo", unique("P"),
                                "creditNo", creditNo,
                                "amount", new BigDecimal("10.0000"),
                                "presentationDate", LocalDate.now().toString(),
                                "documents", List.of(Map.of("documentType", "INVOICE", "copies", 1))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AMENDMENT_PENDING_PRESENTATION_BLOCKED"));

        // 接受范围不一致 -> 422
        mockMvc.perform(post("/api/amendments/" + amendmentNo + "/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "eventNo", unique("EVT"),
                                "accepted", true,
                                "targetMaxAmount", new BigDecimal("900.0000"),
                                "targetExpiryDate", LocalDate.now().plusMonths(6).toString(),
                                "targetAllowedDocumentTypes", List.of("INVOICE"),
                                "acceptedFields", List.of("EXPIRY_DATE", "MAX_AMOUNT"),
                                "decidedBy", "beneficiary-1"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AMENDMENT_SCOPE_MISMATCH"));

        // 完全一致的接受 -> 生效，版本变为 2
        mockMvc.perform(post("/api/amendments/" + amendmentNo + "/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "eventNo", unique("EVT"),
                                "accepted", true,
                                "targetMaxAmount", new BigDecimal("800.0000"),
                                "targetExpiryDate", LocalDate.now().plusMonths(6).toString(),
                                "targetAllowedDocumentTypes", List.of("INVOICE"),
                                "acceptedFields", List.of("EXPIRY_DATE", "MAX_AMOUNT"),
                                "decidedBy", "beneficiary-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.decision.accepted").value(true));

        // 版本差异查询
        mockMvc.perform(get("/api/credits/" + creditNo + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNo").value(1))
                .andExpect(jsonPath("$[0].status").value("SUPERSEDED"))
                .andExpect(jsonPath("$[0].maxAmount").value(1000.0000))
                .andExpect(jsonPath("$[1].versionNo").value(2))
                .andExpect(jsonPath("$[1].status").value("CURRENT"))
                .andExpect(jsonPath("$[1].maxAmount").value(800.0000))
                .andExpect(jsonPath("$[1].sourceAmendmentNo").value(amendmentNo));

        // 旧交单仍绑定版本 1，可按旧条款承兑（统一信封：800 − 0 足够）
        mockMvc.perform(post("/api/presentations/" + presentationNo + "/acceptance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", new BigDecimal("200.0000"),
                                "acceptedBy", "officer-1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditVersionNo").value(1));

        // 余额流水：承兑 + 修订结转
        mockMvc.perform(get("/api/credits/" + creditNo + "/balance-movements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("AMENDMENT_EFFECT"))
                .andExpect(jsonPath("$[0].creditVersionNoAfter").value(2))
                .andExpect(jsonPath("$[1].type").value("ACCEPTANCE"))
                .andExpect(jsonPath("$[1].acceptedAmountAfter").value(200.0000));

        // 当前余额：最高 800、已承兑 200、可用 600
        mockMvc.perform(get("/api/credits/" + creditNo + "/balance"))
                .andExpect(jsonPath("$.maxAmount").value(800.0000))
                .andExpect(jsonPath("$.acceptedAmount").value(200.0000))
                .andExpect(jsonPath("$.availableAmount").value(600.0000))
                .andExpect(jsonPath("$.currentVersionNo").value(2));
    }

    @Test
    void rejectAndWithdrawFlowOverHttp() throws Exception {
        String creditNo = unique("LC");
        mockMvc.perform(post("/api/credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "creditNo", creditNo,
                                "beneficiary", "受益人拒绝",
                                "currency", "USD",
                                "maxAmount", new BigDecimal("1000.0000"),
                                "expiryDate", LocalDate.now().plusMonths(3).toString(),
                                "allowedDocumentTypes", List.of("INVOICE")))))
                .andExpect(status().isCreated());

        // 修订 1：受益人拒绝，版本不变
        String amd1 = unique("AMD");
        mockMvc.perform(post("/api/credits/" + creditNo + "/amendments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amendmentNo", amd1,
                                "proposedMaxAmount", new BigDecimal("900.0000"),
                                "proposedExpiryDate", LocalDate.now().plusMonths(3).toString(),
                                "proposedAllowedDocumentTypes", List.of("INVOICE"),
                                "proposedBy", "applicant-1"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/amendments/" + amd1 + "/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "eventNo", unique("EVT"),
                                "accepted", false,
                                "decidedBy", "beneficiary-1",
                                "reason", "不同意降额"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decision.reason").value("不同意降额"));
        mockMvc.perform(get("/api/credits/" + creditNo + "/balance"))
                .andExpect(jsonPath("$.currentVersionNo").value(1));

        // 修订 2：撤回策略，生效后未承兑交单被撤回
        String pNo = unique("P");
        mockMvc.perform(post("/api/presentations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "presentationNo", pNo,
                                "creditNo", creditNo,
                                "amount", new BigDecimal("200.0000"),
                                "presentationDate", LocalDate.now().toString(),
                                "documents", List.of(Map.of("documentType", "INVOICE", "copies", 1))))))
                .andExpect(status().isCreated());

        String amd2 = unique("AMD");
        mockMvc.perform(post("/api/credits/" + creditNo + "/amendments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amendmentNo", amd2,
                                "proposedMaxAmount", new BigDecimal("500.0000"),
                                "proposedExpiryDate", LocalDate.now().plusMonths(8).toString(),
                                "proposedAllowedDocumentTypes", List.of("INVOICE"),
                                "pendingPolicy", "WITHDRAW_AND_RESUBMIT",
                                "proposedBy", "applicant-1"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/amendments/" + amd2 + "/decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "eventNo", unique("EVT"),
                                "accepted", true,
                                "targetMaxAmount", new BigDecimal("500.0000"),
                                "targetExpiryDate", LocalDate.now().plusMonths(8).toString(),
                                "targetAllowedDocumentTypes", List.of("INVOICE"),
                                "acceptedFields", List.of("EXPIRY_DATE", "MAX_AMOUNT"),
                                "decidedBy", "beneficiary-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // 交单已撤回 -> 承兑 409
        mockMvc.perform(post("/api/presentations/" + pNo + "/acceptance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", new BigDecimal("200.0000"),
                                "acceptedBy", "officer-1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRESENTATION_WITHDRAWN"));

        // 修订列表保留两笔申请与决定
        mockMvc.perform(get("/api/credits/" + creditNo + "/amendments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[1].status").value("ACCEPTED"));
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
