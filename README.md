# cc-letter-credit

信用证与交单资料管理服务，实现跟单信用证下的**交单审核、差异处理、（部分）承兑**
以及**信用证修订（修改）与版本管理**。

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
| `LetterCredit` 信用证 | 编号、受益人、币种；维护全证已承兑占用金额、当前信用证版本号与 JPA 乐观锁版本。可变条款下沉到版本实体 |
| `CreditVersion` 信用证版本 | 不可变版本：版本号（开证为 1）、最高金额、有效期、允许单据类型、生成时冻结的累计已承兑/剩余金额快照；修订生效追加新版本，旧版本永久保留 |
| `Presentation` 交单 | 外部交单号（全局唯一，承兑幂等键）、金额、交单日期、状态（PRESENTED / ACCEPTED / WITHDRAWN），**绑定交单时所依据的信用证版本** |
| `ReviewVersion` 审核版本 | 每次审核的单据快照 + 固化差异清单；补交单据产生新版本，旧版本永久保留 |
| `DiscrepancyDecision` 差异决定 | 申请人对某一审核版本差异的接受决定，接受范围必须与当前差异完全一致 |
| `Acceptance` 承兑台账 | 承兑结果不可修改，记录所依据的信用证版本号；仅可通过独立撤销决定追加撤销状态、原因与处理人 |
| `Amendment` 修订 | 申请人提出的修改申请（最高金额/有效期/允许单据类型），含修订号、修订序号、基线版本、冻结快照、状态（PROPOSED / EFFECTIVE / REJECTED / CANCELLED）与取消留痕 |
| `AmendmentDecision` 修订决定 | 受益人接受/拒绝决定事件：决定事件号幂等、确认的字段变化集合、未承兑交单归属策略 |
| `BalanceChange` 余额变动流水 | 只追加：承兑占用（ACCEPTED）、撤销恢复（REVERSED）、修订生效（AMENDMENT_EFFECTIVE）的前后最高金额/已承兑金额 |

## 主要业务规则

### 1. 开证与交单审核

- 开证生成**信用证版本 1**，记录受益人、币种、最高金额、有效期和允许的单据类型。
- 交单包含外部交单号、金额和多份单据摘要（类型、份数、描述），交单发生时
  **绑定信用证当前版本**。
- 信用证的单据类型清单既是**允许范围**（出现清单外单据 → `DOC_TYPE_NOT_ALLOWED` 差异），
  也是**应交单清单**（缺少规定单据 → `MISSING_REQUIRED_DOCUMENT` 差异，可通过补交消除）。
- 条款类规则（单据类型、有效期）按交单**所依据的信用证版本**审核；
  交单金额超过信用证**实时**可用余额 → `AMOUNT_EXCEEDS_BALANCE`。
- 每次审核形成**不可修改**的差异清单，随审核版本快照固化。

### 2. 差异接受

- 无差异交单可以直接承兑。
- 有差异交单必须先由申请人登记差异接受决定，且接受的差异键集合必须与当前版本差异
  **完全一致**——少接受、多接受均拒绝（`DISCREPANCY_SCOPE_MISMATCH`）。
- 差异键形如 `DOC_TYPE_NOT_ALLOWED:BILL_OF_LADING`、`MISSING_REQUIRED_DOCUMENT:PACKING_LIST`、
  `LC_EXPIRED`，在审核版本查询结果中随差异一并返回。

### 3. 承兑与并发控制

- **部分承兑**：承兑金额可为小于交单金额的正数，但不得超过交单金额。
- **累计承兑不超过信用证余额**（当前版本最高金额 − 已承兑金额）；余额不足整笔回滚。
- 承兑在**同一事务**内完成：版本/差异校验 → 扣减信用证可用金额 → 写承兑台账 → 冻结交单
  → 登记余额变动流水。
- **幂等**：外部交单号重复承兑时返回既有承兑记录，不重复扣款。
- **并发安全**：承兑、撤销与修订生效统一加锁顺序（先相关交单行、后信用证行），
  加锁后显式刷新实体读取最新状态，叠加信用证 JPA 乐观锁版本；余额校验全部在锁内基于
  **实时余额**重算，绝不使用旧余额快照，并发承兑绝不超额。
- **乐观并发校验**：
  - `expectedReviewVersionNo` 与当前最新审核版本不一致 → `REVIEW_VERSION_STALE`（409）；
  - `expectedCreditVersion` 与当前信用证余额版本不一致 → `CREDIT_VERSION_STALE`（409）。

### 4. 补交单据与版本保留

- 未承兑交单可补交单据，系统以累计单据集合按**交单绑定的信用证版本条款**与实时余额
  重新审核并产生**新审核版本**。
- 旧版本及其差异接受决定原样保留，可通过交单详情查询全部版本。
- 交单一旦承兑即冻结：不能补交单据，也不能再登记差异决定。

### 5. 撤销

- 已承兑记录**不得修改**，只能通过独立的撤销决定处理。
- 撤销必须提供完整原因与处理人；同一事务内恢复信用证可用余额并登记余额变动流水。
- 承兑原始要素（金额、承兑人、承兑时间、依据版本）保持不变，撤销信息另行留痕；
  已撤销记录不可重复撤销。

### 6. 信用证修订（修改）

**提出修订（申请人）**

- 可修改**最高金额、有效期、允许单据类型**三类条款，至少修改一项，否则
  `AMENDMENT_NO_TERMS_CHANGED`；字段可整体给出修订后的完整条款。
- 创建时**冻结信用证当前版本号**（基线版本）与**剩余金额快照**（累计已承兑、可用金额、
  未承兑交单数量），随修订留痕。
- **降低最高金额不得低于累计已承兑金额**（承兑不可撤销），否则
  `AMENDMENT_AMOUNT_BELOW_ACCEPTED`。
- **同一信用证同时只能有一笔活动修订**（PROPOSED），否则 `ACTIVE_AMENDMENT_EXISTS`。
- 修订号（`amendmentNo`）全局唯一且**幂等**：同号同内容重复提交返回原修订，
  同号不同内容返回 `DUPLICATE_AMENDMENT_NO`。

**受益人决定**

- 修订须经**受益人接受**才生效；也可**拒绝**。决定事件号（`decisionEventNo`）
  全局唯一、**幂等**。
- 接受时声明的字段变化集合必须与修订相对基线的**实际变化字段完全一致**
  （键：`MAX_AMOUNT` / `EXPIRY_DATE` / `ALLOWED_DOCUMENT_TYPES`），少确认/多确认均拒绝
  （`AMENDMENT_SCOPE_MISMATCH`）。
- **修订对既有未承兑交单的影响必须在接受时明确归属**：
  - `KEEP_OLD_VERSION` 继续旧版本：这些交单不按新版本重审，仍按交单时的旧版本条款
    补交、处理差异与承兑；此时修订后最高金额仍须**覆盖「累计已承兑 + 这些未承兑交单金额」**，
    否则 `PENDING_PRESENTATIONS_NOT_COVERED`——不能让尚未处理的交单金额失去明确归属。
  - `WITHDRAW_RESUBMIT` 撤回后按新版本补交：这些交单随修订生效一并置为 `WITHDRAWN`
    终态，不能再补交或承兑，需重新交单；因交单撤回，最高金额只需不低于累计已承兑。
  - 存在未承兑交单却未给策略 → `PENDING_PRESENTATION_POLICY_REQUIRED`。

**生效**

- 接受在锁内重新校验**当前版本仍为基线版本**（否则 `AMENDMENT_BASE_VERSION_STALE`）
  与**实时累计承兑**，通过后追加不可变的**新信用证版本**（以实时累计承兑冻结剩余快照），
  登记 `AMENDMENT_EFFECTIVE` 余额变动流水。
- **旧信用证版本与既有承兑保持不可修改**；承兑台账记录承兑时所依据的信用证版本号。
- 拒绝（`REJECTED`）或取消（`CANCELLED`，需取消事件号、处理人、原因，取消事件号幂等）
  后**保留申请与决定留痕，但不改变当前信用证版本**；之后可提出新的修订，修订序号递增。

## 查询接口

- `GET /api/credits/{creditNo}/balance`：信用证余额（当前版本最高/已承兑/可用金额、当前版本号、乐观锁版本）。
- `GET /api/credits/{creditNo}/versions` 与 `/versions/{versionNo}`：信用证全部/指定版本（旧版本保留）。
- `GET /api/credits/{creditNo}/versions/diff?from=1&to=2`：**信用证版本差异**（不传时比较相邻最新两版本）。
- `GET /api/credits/{creditNo}/balance-changes`：**余额变化流水**（承兑/撤销/修订生效）。
- `GET /api/credits/{creditNo}/amendments` 与 `/amendments/{amendmentNo}`：修订列表/详情（含冻结快照与受益人决定）。
- `GET /api/presentations/{presentationNo}` 与 `.../versions`：交单（含**所依据信用证版本号** `creditVersionNo`）与全部审核版本。
- `GET /api/presentations/{presentationNo}/discrepancy-decisions`：差异接受决定。
- `GET /api/credits/{creditNo}/acceptances?status=ACCEPTED|REVERSED`：承兑台账（含 `creditVersionNo`）。
- `GET /api/acceptances/{acceptanceNo}`、`GET /api/presentations/{no}/acceptance`：单笔承兑。

## 操作接口示例

```http
# 开证（生成信用证版本 1）
POST /api/credits
{
  "creditNo": "LC-001",
  "beneficiary": "受益人甲",
  "currency": "USD",
  "maxAmount": 1000.0000,
  "expiryDate": "2026-12-31",
  "allowedDocumentTypes": ["INVOICE", "PACKING_LIST"]
}

# 交单（绑定当前信用证版本，立即形成审核版本 1）
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

# 补交单据（产生新审核版本）
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

# 申请人提出修订（提高金额、延长效期、增加单据类型）
POST /api/credits/LC-001/amendments
{
  "amendmentNo": "AM-20261001-001",
  "newMaxAmount": 1500.0000,
  "newExpiryDate": "2027-06-30",
  "newAllowedDocumentTypes": ["INVOICE", "PACKING_LIST", "BILL_OF_LADING"],
  "proposedBy": "applicant-1"
}

# 受益人接受：确认字段变化范围必须与修订完全一致；
# 有未承兑交单时明确其归属（KEEP_OLD_VERSION 继续旧版本 / WITHDRAW_RESUBMIT 撤回重交）
POST /api/credits/LC-001/amendments/AM-20261001-001/decision
{
  "decisionEventNo": "EVT-20261002-001",
  "accepted": true,
  "acceptedChangedFields": ["MAX_AMOUNT", "EXPIRY_DATE", "ALLOWED_DOCUMENT_TYPES"],
  "pendingPresentationPolicy": "KEEP_OLD_VERSION",
  "decidedBy": "beneficiary-1",
  "remark": "同意修改"
}

# 受益人拒绝（留痕，不产生新版本）
POST /api/credits/LC-001/amendments/AM-20261001-001/decision
{"decisionEventNo": "EVT-20261002-002", "accepted": false, "decidedBy": "beneficiary-1", "remark": "不同意"}

# 申请人在决定前取消（取消事件号幂等，当前版本不变）
POST /api/credits/LC-001/amendments/AM-20261001-001/cancellation
{"cancelEventNo": "CEV-20261001-001", "cancelledBy": "applicant-1", "reason": "条款需要调整"}
```

## 错误响应

统一返回 `{ "code", "message", "timestamp" }`：资源不存在 404；请求参数/前置取值非法
400（含修订无变化、降额低于已承兑、继续旧版本但额度无法覆盖未承兑交单、接受范围不一致、
未明确未承兑交单归属）；并发/状态冲突 409（重复交单号/修订号、存在活动修订、
修订已终态、版本过期、交单已撤回、重复撤销等）；承兑前置条件不满足（未接受差异等）422；
参数校验失败 400。

## 测试

- `ReviewEngineTest`：审核规则纯单元测试（清单外单据、缺少单据、效期、超额）。
- `LetterCreditWorkflowTest`：服务层端到端规则测试，覆盖部分承兑、累计余额上限、
  差异精确匹配、补交新版本、版本过期失败、交单号幂等、**多线程并发承兑不超额**、
  撤销恢复余额与台账过滤等 17 个场景。
- `AmendmentWorkflowTest`：修订全流程 15 个场景——创建冻结版本与剩余金额、活动修订唯一、
  修订号/决定事件号/取消事件号幂等、降额下限、接受字段范围精确匹配、未承兑交单继续旧版本/
  撤回重交两种归属、旧版本与既有承兑不可变、拒绝/取消留痕不改版本、版本差异与余额变化查询、
  **修订生效与承兑并发时锁内按实时余额重校验绝不超额**。
- `LetterCreditApiTest`：MockMvc HTTP 全流程测试（无差异流程 / 差异-补交-撤销流程 /
  修订接受-继续旧版本 / 拒绝-取消 / 撤回重交 / 404）。
