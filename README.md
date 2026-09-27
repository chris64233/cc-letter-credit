# cc-letter-credit

信用证与交单资料管理服务，实现跟单信用证下的**交单审核、差异处理与（部分）承兑**。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1（Spring Data JPA + Bean Validation + H2）

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run

## 领域模型

| 实体 | 说明 |
| --- | --- |
| `LetterCredit` 信用证 | 编号、受益人、币种、最高金额、有效期、允许的单据类型；维护已承兑占用金额与 JPA 乐观锁版本 |
| `Presentation` 交单 | 外部交单号（全局唯一，承兑幂等键）、金额、交单日期、状态（PRESENTED / ACCEPTED） |
| `ReviewVersion` 审核版本 | 每次审核的单据快照 + 固化差异清单；补交单据产生新版本，旧版本永久保留 |
| `DiscrepancyDecision` 差异决定 | 申请人对某一版本差异的接受决定，接受范围必须与当前差异完全一致 |
| `Acceptance` 承兑台账 | 承兑结果不可修改；仅可通过独立撤销决定追加撤销状态、原因与处理人 |

## 主要业务规则

### 1. 开证与交单审核

- 信用证记录受益人、币种、最高金额、有效期和允许的单据类型。
- 交单包含外部交单号、金额和多份单据摘要（类型、份数、描述）。
- 信用证的单据类型清单既是**允许范围**（出现清单外单据 → `DOC_TYPE_NOT_ALLOWED` 差异），
  也是**应交单清单**（缺少规定单据 → `MISSING_REQUIRED_DOCUMENT` 差异，可通过补交消除）。
- 其他审核规则：交单日期晚于有效期 → `LC_EXPIRED`；交单金额超过信用证当前可用余额
  → `AMOUNT_EXCEEDS_BALANCE`。
- 每次审核形成**不可修改**的差异清单，随审核版本快照固化。

### 2. 差异接受

- 无差异交单可以直接承兑。
- 有差异交单必须先由申请人登记差异接受决定，且接受的差异键集合必须与当前版本差异
  **完全一致**——少接受、多接受均拒绝（`DISCREPANCY_SCOPE_MISMATCH`）。
- 差异键形如 `DOC_TYPE_NOT_ALLOWED:BILL_OF_LADING`、`MISSING_REQUIRED_DOCUMENT:PACKING_LIST`、
  `LC_EXPIRED`，在审核版本查询结果中随差异一并返回。

### 3. 承兑与并发控制

- **部分承兑**：承兑金额可为小于交单金额的正数，但不得超过交单金额。
- **累计承兑不超过信用证余额**（最高金额 − 已承兑金额）；余额不足整笔回滚。
- 承兑在**同一事务**内完成：版本/差异校验 → 扣减信用证可用金额 → 写承兑台账 → 冻结交单。
- **幂等**：外部交单号重复承兑时返回既有承兑记录，不重复扣款。
- **并发安全**：承兑事务对交单行与信用证行加悲观写锁（固定加锁顺序防死锁），
  叠加信用证 JPA 乐观锁版本，并发承兑绝不超额。
- **乐观并发校验**：
  - `expectedReviewVersionNo` 与当前最新审核版本不一致 → `REVIEW_VERSION_STALE`（409）；
  - `expectedCreditVersion` 与当前信用证余额版本不一致 → `CREDIT_VERSION_STALE`（409）。

### 4. 补交单据与版本保留

- 未承兑交单可补交单据，系统以累计单据集合重新审核并产生**新审核版本**。
- 旧版本及其差异接受决定原样保留，可通过交单详情查询全部版本。
- 交单一旦承兑即冻结：不能补交单据，也不能再登记差异决定。

### 5. 撤销

- 已承兑记录**不得修改**，只能通过独立的撤销决定处理。
- 撤销必须提供完整原因与处理人；同一事务内恢复信用证可用余额。
- 承兑原始要素（金额、承兑人、承兑时间、依据版本）保持不变，撤销信息另行留痕；
  已撤销记录不可重复撤销。

## 查询接口

- `GET /api/credits/{creditNo}/balance`：信用证余额（最高/已承兑/可用金额、乐观锁版本）。
- `GET /api/presentations/{presentationNo}` 与 `.../versions`：交单与全部审核版本。
- `GET /api/presentations/{presentationNo}/discrepancy-decisions`：差异接受决定。
- `GET /api/credits/{creditNo}/acceptances?status=ACCEPTED|REVERSED`：承兑台账。
- `GET /api/acceptances/{acceptanceNo}`、`GET /api/presentations/{no}/acceptance`：单笔承兑。

## 操作接口示例

```http
# 开证
POST /api/credits
{
  "creditNo": "LC-001",
  "beneficiary": "受益人甲",
  "currency": "USD",
  "maxAmount": 1000.0000,
  "expiryDate": "2026-12-31",
  "allowedDocumentTypes": ["INVOICE", "PACKING_LIST"]
}

# 交单（立即形成审核版本 1）
POST /api/presentations
{
  "presentationNo": "EXT-20260927-001",
  "creditNo": "LC-001",
  "amount": 300.0000,
  "presentationDate": "2026-09-27",
  "documents": [
    {"documentType": "INVOICE", "copies": 3, "description": "商业发票"}
  ]
}

# 补交单据（产生新版本）
POST /api/presentations/EXT-20260927-001/versions
{"documents": [{"documentType": "PACKING_LIST", "copies": 2}]}

# 申请人接受当前版本全部差异
POST /api/presentations/EXT-20260927-001/discrepancy-decisions
{"acceptedKeys": ["MISSING_REQUIRED_DOCUMENT:PACKING_LIST"], "acceptedBy": "applicant-1"}

# （部分）承兑；可携带版本号做乐观校验
POST /api/presentations/EXT-20260927-001/acceptance
{
  "amount": 250.0000,
  "expectedReviewVersionNo": 2,
  "expectedCreditVersion": 0,
  "acceptedBy": "officer-1"
}

# 独立撤销
POST /api/acceptances/ACC-EXT-20260927-001/reversal
{"reversedBy": "manager-9", "reason": "单据退回，经审批撤销承兑"}
```

## 错误响应

统一返回 `{ "code", "message", "timestamp" }`：资源不存在 404；并发/状态冲突
（重复交单号、版本过期、重复撤销等）409；承兑前置条件不满足（未接受差异、
接受范围不一致等）422；参数校验失败 400。

## 测试

- `ReviewEngineTest`：审核规则纯单元测试（清单外单据、缺少单据、效期、超额）。
- `LetterCreditWorkflowTest`：服务层端到端规则测试，覆盖部分承兑、累计余额上限、
  差异精确匹配、补交新版本、版本过期失败、交单号幂等、**多线程并发承兑不超额**、
  撤销恢复余额与台账过滤等 16 个场景。
- `LetterCreditApiTest`：MockMvc HTTP 全流程测试（无差异流程 / 差异-补交-撤销流程 / 404）。
