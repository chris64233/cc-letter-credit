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
 * 信用证全流程 HTTP 接口测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
class LetterCreditApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullCleanFlowOverHttp() throws Exception {
        String creditNo = unique("LC");

        // 开证
        mockMvc.perform(post("/api/credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "creditNo", creditNo,
                                "beneficiary", "受益人 API",
                                "currency", "USD",
                                "maxAmount", new BigDecimal("1000.0000"),
                                "expiryDate", LocalDate.now().plusMonths(3).toString(),
                                "allowedDocumentTypes", List.of("INVOICE")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.availableAmount").value(1000.0000))
                .andExpect(jsonPath("$.version").value(0));

        // 参数校验失败
        mockMvc.perform(post("/api/credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.creditNo").exists());

        // 无差异交单
        String presentationNo = unique("P");
        mockMvc.perform(post("/api/presentations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "presentationNo", presentationNo,
                                "creditNo", creditNo,
                                "amount", new BigDecimal("400.0000"),
                                "presentationDate", LocalDate.now().toString(),
                                "documents", List.of(Map.of(
                                        "documentType", "INVOICE", "copies", 3,
                                        "description", "商业发票"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latestVersionNo").value(1))
                .andExpect(jsonPath("$.versions[0].clean").value(true))
                .andExpect(jsonPath("$.versions[0].discrepancies").isEmpty());

        // 重复交单号 -> 409
        mockMvc.perform(post("/api/presentations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "presentationNo", presentationNo,
                                "creditNo", creditNo,
                                "amount", new BigDecimal("400.0000"),
                                "presentationDate", LocalDate.now().toString(),
                                "documents", List.of(Map.of("documentType", "INVOICE", "copies", 1))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PRESENTATION_NO"));

        // 部分承兑
        mockMvc.perform(post("/api/presentations/" + presentationNo + "/acceptance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", new BigDecimal("250.0000"),
                                "acceptedBy", "officer-1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(250.0000))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.reviewVersionNo").value(1));

        // 幂等：重复承兑返回同一条记录，金额不重复扣减
        mockMvc.perform(post("/api/presentations/" + presentationNo + "/acceptance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", new BigDecimal("250.0000"),
                                "acceptedBy", "officer-1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(get("/api/credits/" + creditNo + "/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedAmount").value(250.0000))
                .andExpect(jsonPath("$.availableAmount").value(750.0000))
                .andExpect(jsonPath("$.version").value(1));

        // 台账
        mockMvc.perform(get("/api/credits/" + creditNo + "/acceptances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void discrepancyDecisionSupplementAndReversalFlowOverHttp() throws Exception {
        String creditNo = unique("LC");

        mockMvc.perform(post("/api/credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "creditNo", creditNo,
                                "beneficiary", "受益人差异",
                                "currency", "EUR",
                                "maxAmount", new BigDecimal("1000.0000"),
                                "expiryDate", LocalDate.now().plusMonths(3).toString(),
                                "allowedDocumentTypes", List.of("INVOICE", "PACKING_LIST")))))
                .andExpect(status().isCreated());

        String presentationNo = unique("P");
        mockMvc.perform(post("/api/presentations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "presentationNo", presentationNo,
                                "creditNo", creditNo,
                                "amount", new BigDecimal("100.0000"),
                                "presentationDate", LocalDate.now().toString(),
                                "documents", List.of(Map.of(
                                        "documentType", "INVOICE", "copies", 1))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versions[0].clean").value(false))
                .andExpect(jsonPath("$.versions[0].discrepancies[0].key")
                        .value("MISSING_REQUIRED_DOCUMENT:PACKING_LIST"));

        // 未接受差异直接承兑 -> 422
        mockMvc.perform(post("/api/presentations/" + presentationNo + "/acceptance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", new BigDecimal("100.0000"),
                                "acceptedBy", "officer-1"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DISCREPANCY_DECISION_REQUIRED"));

        // 接受范围不一致 -> 422
        mockMvc.perform(post("/api/presentations/" + presentationNo + "/discrepancy-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "acceptedKeys", List.of("LC_EXPIRED"),
                                "acceptedBy", "applicant-1"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DISCREPANCY_SCOPE_MISMATCH"));

        // 精确接受全部差异
        mockMvc.perform(post("/api/presentations/" + presentationNo + "/discrepancy-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "acceptedKeys",
                                List.of("MISSING_REQUIRED_DOCUMENT:PACKING_LIST"),
                                "acceptedBy", "applicant-1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNo").value(1));

        // 补交缺失单据 -> 新版本 2 无差异，旧版本保留
        mockMvc.perform(post("/api/presentations/" + presentationNo + "/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "documents", List.of(Map.of(
                                        "documentType", "PACKING_LIST", "copies", 2))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latestVersionNo").value(2))
                .andExpect(jsonPath("$.versions.length()").value(2))
                .andExpect(jsonPath("$.versions[0].discrepancyAccepted").value(true))
                .andExpect(jsonPath("$.versions[1].clean").value(true));

        // 依据旧版本 1 承兑 -> 409
        mockMvc.perform(post("/api/presentations/" + presentationNo + "/acceptance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", new BigDecimal("100.0000"),
                                "expectedReviewVersionNo", 1,
                                "acceptedBy", "officer-1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_VERSION_STALE"));

        // 依据新版本 2 承兑成功
        String acceptedBody = mockMvc.perform(post("/api/presentations/" + presentationNo + "/acceptance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "amount", new BigDecimal("100.0000"),
                                "expectedReviewVersionNo", 2,
                                "acceptedBy", "officer-1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewVersionNo").value(2))
                .andReturn().getResponse().getContentAsString();
        String acceptanceNo = objectMapper.readTree(acceptedBody).get("acceptanceNo").asText();

        // 已承兑交单补交单据 -> 409
        mockMvc.perform(post("/api/presentations/" + presentationNo + "/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "documents", List.of(Map.of("documentType", "INVOICE", "copies", 1))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRESENTATION_ALREADY_ACCEPTED"));

        // 独立撤销：原因与处理人留痕，余额恢复
        mockMvc.perform(post("/api/acceptances/" + acceptanceNo + "/reversal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reversedBy", "manager-9",
                                "reason", "单据被退回，审批撤销"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERSED"))
                .andExpect(jsonPath("$.reversedBy").value("manager-9"))
                .andExpect(jsonPath("$.reversalReason").value("单据被退回，审批撤销"));

        mockMvc.perform(get("/api/credits/" + creditNo + "/balance"))
                .andExpect(jsonPath("$.availableAmount").value(1000.0000));

        // 重复撤销 -> 409
        mockMvc.perform(post("/api/acceptances/" + acceptanceNo + "/reversal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reversedBy", "manager-9",
                                "reason", "再次撤销"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCEPTANCE_ALREADY_REVERSED"));
    }

    @Test
    void missingResourceReturns404() throws Exception {
        mockMvc.perform(get("/api/credits/" + unique("NOPE") + "/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CREDIT_NOT_FOUND"));
    }

    @Test
    void amendmentAcceptKeepOldVersionCreatesCreditVersionOverHttp() throws Exception {
        String creditNo = unique("LC");
        createCredit(creditNo, new BigDecimal("1000.0000"), LocalDate.now().plusMonths(3),
                List.of("INVOICE"));

        // 一笔未承兑交单（版本 1 下无差异）
        String presentationNo = unique("P");
        present(creditNo, presentationNo, new BigDecimal("100.0000"), List.of("INVOICE"));

        String amendmentNo = unique("AM");
        // 提出修订：提高金额、延长效期、增加单据类型
        mockMvc.perform(post("/api/credits/" + creditNo + "/amendments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "amendmentNo", amendmentNo,
                                "newMaxAmount", new BigDecimal("1500.0000"),
                                "newExpiryDate", LocalDate.now().plusMonths(6).toString(),
                                "newAllowedDocumentTypes", List.of("INVOICE", "PACKING_LIST"),
                                "proposedBy", "applicant-1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROPOSED"))
                .andExpect(jsonPath("$.baseVersionNo").value(1))
                .andExpect(jsonPath("$.frozenPendingPresentations").value(1))
                .andExpect(jsonPath("$.changedFields.length()").value(3));

        // 第二笔活动修订 -> 409
        mockMvc.perform(post("/api/credits/" + creditNo + "/amendments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "amendmentNo", unique("AM"),
                                "newMaxAmount", new BigDecimal("1200.0000"),
                                "newExpiryDate", LocalDate.now().plusMonths(6).toString(),
                                "newAllowedDocumentTypes", List.of("INVOICE"),
                                "proposedBy", "applicant-1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_AMENDMENT_EXISTS"));

        // 存在未承兑交单却不给归属策略 -> 400
        mockMvc.perform(post("/api/credits/" + creditNo + "/amendments/" + amendmentNo + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "decisionEventNo", "EVT-" + unique("X"),
                                "accepted", true,
                                "acceptedChangedFields",
                                List.of("MAX_AMOUNT", "EXPIRY_DATE", "ALLOWED_DOCUMENT_TYPES"),
                                "decidedBy", "beneficiary-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PENDING_PRESENTATION_POLICY_REQUIRED"));

        // 接受范围不一致 -> 400
        mockMvc.perform(post("/api/credits/" + creditNo + "/amendments/" + amendmentNo + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "decisionEventNo", "EVT-" + unique("X"),
                                "accepted", true,
                                "acceptedChangedFields", List.of("MAX_AMOUNT"),
                                "pendingPresentationPolicy", "KEEP_OLD_VERSION",
                                "decidedBy", "beneficiary-1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMENDMENT_SCOPE_MISMATCH"));

        // 精确接受 + 继续旧版本 -> 生效，生成版本 2
        mockMvc.perform(post("/api/credits/" + creditNo + "/amendments/" + amendmentNo + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "decisionEventNo", "EVT-ACCEPT-1",
                                "accepted", true,
                                "acceptedChangedFields",
                                List.of("MAX_AMOUNT", "EXPIRY_DATE", "ALLOWED_DOCUMENT_TYPES"),
                                "pendingPresentationPolicy", "KEEP_OLD_VERSION",
                                "decidedBy", "beneficiary-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EFFECTIVE"))
                .andExpect(jsonPath("$.decision.pendingPresentationPolicy")
                        .value("KEEP_OLD_VERSION"));

        // 余额视图切到版本 2，最高金额变化
        mockMvc.perform(get("/api/credits/" + creditNo + "/balance"))
                .andExpect(jsonPath("$.currentVersionNo").value(2))
                .andExpect(jsonPath("$.maxAmount").value(1500.0000));

        // 版本列表与差异
        mockMvc.perform(get("/api/credits/" + creditNo + "/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNo").value(1))
                .andExpect(jsonPath("$[1].versionNo").value(2))
                .andExpect(jsonPath("$[1].kind").value("AMENDED"));
        mockMvc.perform(get("/api/credits/" + creditNo + "/versions/diff?from=1&to=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxAmountBefore").value(1000.0000))
                .andExpect(jsonPath("$.maxAmountAfter").value(1500.0000))
                .andExpect(jsonPath("$.docTypesAfter[1]").value("PACKING_LIST"));

        // 旧交单仍依据版本 1，可承兑
        mockMvc.perform(post("/api/presentations/" + presentationNo + "/acceptance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "amount", new BigDecimal("100.0000"),
                                "acceptedBy", "officer-1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.creditVersionNo").value(1));

        // 余额变化流水：修订生效 + 承兑
        mockMvc.perform(get("/api/credits/" + creditNo + "/balance-changes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].changeType").value("AMENDMENT_EFFECTIVE"))
                .andExpect(jsonPath("$[1].changeType").value("ACCEPTED"))
                .andExpect(jsonPath("$[1].acceptedAfter").value(100.0000));
    }

    @Test
    void amendmentRejectAndCancelKeepApplicationAndCreditVersionOverHttp() throws Exception {
        String creditNo = unique("LC");
        createCredit(creditNo, new BigDecimal("1000.0000"), LocalDate.now().plusMonths(3),
                List.of("INVOICE"));

        // 拒绝：版本不变，决定保留
        String rejectNo = unique("AM");
        mockMvc.perform(post("/api/credits/" + creditNo + "/amendments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "amendmentNo", rejectNo,
                                "newMaxAmount", new BigDecimal("1200.0000"),
                                "newExpiryDate", LocalDate.now().plusMonths(3).toString(),
                                "newAllowedDocumentTypes", List.of("INVOICE"),
                                "proposedBy", "applicant-1"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/credits/" + creditNo + "/amendments/" + rejectNo + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "decisionEventNo", "EVT-REJ-1",
                                "accepted", false,
                                "decidedBy", "beneficiary-1",
                                "remark", "不同意"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decision.accepted").value(false));
        mockMvc.perform(get("/api/credits/" + creditNo + "/balance"))
                .andExpect(jsonPath("$.currentVersionNo").value(1));

        // 第二笔：取消后版本不变、申请留痕
        String cancelNo = unique("AM");
        mockMvc.perform(post("/api/credits/" + creditNo + "/amendments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "amendmentNo", cancelNo,
                                "newMaxAmount", new BigDecimal("1300.0000"),
                                "newExpiryDate", LocalDate.now().plusMonths(3).toString(),
                                "newAllowedDocumentTypes", List.of("INVOICE"),
                                "proposedBy", "applicant-1"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/credits/" + creditNo + "/amendments/" + cancelNo + "/cancellation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "cancelEventNo", "CEV-CAN-1",
                                "cancelledBy", "applicant-1",
                                "reason", "条款再议"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("条款再议"));
        mockMvc.perform(get("/api/credits/" + creditNo + "/balance"))
                .andExpect(jsonPath("$.currentVersionNo").value(1));

        // 两笔申请均保留
        mockMvc.perform(get("/api/credits/" + creditNo + "/amendments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void withdrawResubmitWithdrawsPendingPresentationOverHttp() throws Exception {
        String creditNo = unique("LC");
        createCredit(creditNo, new BigDecimal("1000.0000"), LocalDate.now().plusMonths(3),
                List.of("INVOICE"));
        String presentationNo = unique("P");
        present(creditNo, presentationNo, new BigDecimal("100.0000"), List.of("INVOICE"));

        String amendmentNo = unique("AM");
        mockMvc.perform(post("/api/credits/" + creditNo + "/amendments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "amendmentNo", amendmentNo,
                                "newMaxAmount", new BigDecimal("1000.0000"),
                                "newExpiryDate", LocalDate.now().plusMonths(6).toString(),
                                "newAllowedDocumentTypes", List.of("INVOICE"),
                                "proposedBy", "applicant-1"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/credits/" + creditNo + "/amendments/" + amendmentNo + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "decisionEventNo", "EVT-WD-1",
                                "accepted", true,
                                "acceptedChangedFields", List.of("EXPIRY_DATE"),
                                "pendingPresentationPolicy", "WITHDRAW_RESUBMIT",
                                "decidedBy", "beneficiary-1"))))
                .andExpect(status().isOk());

        // 交单撤回：承兑被拒
        mockMvc.perform(get("/api/presentations/" + presentationNo))
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.creditVersionNo").value(1));
        mockMvc.perform(post("/api/presentations/" + presentationNo + "/acceptance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "amount", new BigDecimal("100.0000"),
                                "acceptedBy", "officer-1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRESENTATION_WITHDRAWN"));
    }

    private void createCredit(String creditNo, BigDecimal maxAmount, LocalDate expiry,
                              List<String> docTypes) throws Exception {
        mockMvc.perform(post("/api/credits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "creditNo", creditNo,
                                "beneficiary", "受益人修订 API",
                                "currency", "USD",
                                "maxAmount", maxAmount,
                                "expiryDate", expiry.toString(),
                                "allowedDocumentTypes", docTypes))))
                .andExpect(status().isCreated());
    }

    private void present(String creditNo, String presentationNo, BigDecimal amount,
                         List<String> docTypes) throws Exception {
        List<java.util.Map<String, Object>> documents = docTypes.stream()
                .map(t -> java.util.Map.<String, Object>of("documentType", t, "copies", 1,
                        "description", t + " 摘要"))
                .toList();
        mockMvc.perform(post("/api/presentations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "presentationNo", presentationNo,
                                "creditNo", creditNo,
                                "amount", amount,
                                "presentationDate", LocalDate.now().toString(),
                                "documents", documents))))
                .andExpect(status().isCreated());
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
