# cc-letter-credit

信用证与交单资料管理服务，实现跟单信用证下的**交单审核、差异处理、（部分）承兑**
以及**信用证修改（修订）与版本管理**。

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
| `LetterCredit` 信用证 | 编号、受益人、币种、**当前版本号**、当前最高金额、有效期、允许的单据类型；维护已承兑占用金额与 JPA 乐观锁版本 |
| `CreditVersion` 信用证版本 | 开证生成版本 1；每次修订生效新增一个不可变版本，旧版本转 `SUPERSEDED`，永久保留 |
| `CreditAmendment` 修订申请 | 申请人对最高金额/有效期/单据清单的修改申请；创建时冻结基础版本号、已承兑累计与剩余金额；同一信用证同时至多一笔 `PROPOSED` |
| `AmendmentDecision` 修订决定 | 受益人接受（生效）或拒绝；决定事件号幂等；接受范围必须与修订目标完全一致 |
| `Presentation` 交单 | 外部交单号（全局唯一，承兑幂等键）、金额、交单日期、状态（PRESENTED / ACCEPTED / **WITHDRAWN**）、**交单时绑定的信用证版本号** |
| `ReviewVersion` 审核版本 | 每次审核的单据快照 + 固化差异清单；补交单据产生新版本，旧版本永久保留 |
| `DiscrepancyDecision` 差异决定 | 申请人对某一审核版本差异的接受决定，接受范围必须与当前差异完全一致 |
| `Acceptance` 承兑台账 | 承兑结果不可修改；记录承兑额度所归属的信用证版本号；仅可通过独立撤销决定追加撤销状态、原因与处理人 |
| `BalanceMovement` 余额流水 | 承兑（+占用）、撤销（−占用）、修订生效（0：版本结转）的不可变余额变化流水 |

## 主要业务规则

### 1. 开证与交单审核

- 信用证记录受益人、币种、最高金额、有效期和允许的单据类型；开证即固化**信用证版本 1**。
- 交单包含外部交单号、金额和多份单据摘要（类型、份数、描述），交单创建时
  **绑定信用证当前版本**。
- 信用证的单据类型清单既是**允许范围**（出现清单外单据 → `DOC_TYPE_NOT_ALLOWED` 差异），
  也是**应交单清单**（缺少规定单据 → `MISSING_REQUIRED_DOCUMENT` 差异，可通过补交消除）。
  审核按交单**所绑定版本**的有效期与单据清单执行。
- 其他审核规则：交单日期晚于所依据版本的有效期 → `LC_EXPIRED`；交单金额超过信用证
  当前可用余额 → `AMOUNT_EXCEEDS_BALANCE`。
- 每次审核形成**不可修改**的差异清单，随审核版本快照固化。

### 2. 差异接受

- 无差异交单可以直接承兑。
- 有差异交单必须先由申请人登记差异接受决定，且接受的差异键集合必须与当前版本差异
  **完全一致**——少接受、多接受均拒绝（`DISCREPANCY_SCOPE_MISMATCH`）。
- 差异键形如 `DOC_TYPE_NOT_ALLOWED:BILL_OF_LADING`、`MISSING_REQUIRED_DOCUMENT:PACKING_LIST`、
  `LC_EXPIRED`，在审核版本查询结果中随差异一并返回。

### 3. 承兑与并发控制

- **部分承兑**：承兑金额可为小于交单金额的正数，但不得超过交单金额。
- **累计承兑不超过当前最高金额**；可用余额 = 当前最高金额 − 全版本未撤销承兑累计
  （统一金额信封，见下节），余额不足整笔回滚。
- 承兑在**同一事务**内完成：版本/差异校验 → 扣减信用证可用金额 → 写承兑台账 → 冻结交单。
- **幂等**：外部交单号重复承兑时返回既有承兑记录，不重复扣款。
- **并发安全**：承兑事务对交单行与信用证行加悲观写锁（固定加锁顺序防死锁），
  叠加信用证 JPA 乐观锁版本；修订生效、承兑、撤销均锁信用证行串行化，
  并发下绝不超额。
- **乐观并发校验**：
  - `expectedReviewVersionNo` 与当前最新审核版本不一致 → `REVIEW_VERSION_STALE`（409）；
  - `expectedCreditVersion` 与当前信用证余额版本不一致 → `CREDIT_VERSION_STALE`（409）。

### 4. 补交单据与版本保留

- 未承兑交单可补交单据，系统以累计单据集合重新审核并产生**新审核版本**。
- 旧版本及其差异接受决定原样保留，可通过交单详情查询全部版本。
- 交单一旦承兑或随修订撤回即冻结：不能补交单据，也不能再登记差异决定。

### 5. 撤销

- 已承兑记录**不得修改**，只能通过独立的撤销决定处理。
- 撤销必须提供完整原因与处理人；同一事务内恢复信用证可用余额。
- 承兑原始要素（金额、承兑人、承兑时间、依据版本）保持不变，撤销信息另行留痕；
  已撤销记录不可重复撤销。

### 6. 信用证修改（修订）

**提出修订**

- 申请人可修改**最高金额、有效期、允许单据类型**中的任意项（至少一项实际变化，
  否则 `AMENDMENT_NO_CHANGE`）；修订号全局唯一、创建幂等。
- 创建时**冻结**：所依据的当前版本号、已承兑累计金额、剩余可用金额，
  以及当时全部未承兑交单号快照；冻结期间信用证条款不变。
- **同一信用证同时只能有一笔活动修订**（`PROPOSED`，否则 `ACTIVE_AMENDMENT_EXISTS`）。
- 活动修订期间暂不受理新交单（`AMENDMENT_PENDING_PRESENTATION_BLOCKED`）：
  接受后按新版本交单，拒绝/取消后按当前版本交单，避免版本冻结窗口内产生
  归属不明的交单。
- **降低最高金额**的两条硬约束：
  1. 不得低于**已承兑累计金额**（既有承兑不可修改）；
  2. 当存在受影响的未承兑交单且申请人选择“继续使用旧版本”时，
     还不得低于“已承兑累计 + 这些未承兑交单金额”，保证每笔尚未处理的交单
     都有明确额度归属；选择“撤回补交”时这些交单不再占用额度。

**受益人决定**

- 修订必须经**受益人接受**才生效；受益人也可拒绝。拒绝/取消后**申请与决定都保留**，
  当前信用证版本不变，之后可提出新修订。
- 当修订改变了既有未承兑交单所依据的字段（三项中任意一项）且确实存在未承兑交单时，
  申请人提出时必须明确处置方式：
  - `KEEP_OLD_VERSION`：这些交单**继续按旧版本**的有效期与单据清单审核/承兑；
  - `WITHDRAW_AND_RESUBMIT`：修订生效时把这些交单置为 `WITHDRAWN` 终态，
    之后须使用新交单号按新版本重新交单。
- 接受时回传的目标金额、有效期、单据清单与受影响字段集合，必须与当前修订版本
  **完全一致**（`AMENDMENT_SCOPE_MISMATCH`）；决定事件号全局唯一、**幂等**。

**生效与不可变性**

- 接受在同一事务内：锁定信用证余额与版本并**重新校验**（防止修订待决期间的
  承兑/撤销使冻结快照失效）→ 旧版本转 `SUPERSEDED` → 生成新版本 → 更新当前条款
  → 按策略处置未承兑交单 → 写余额结转流水。
- 旧信用证版本与既有承兑台账**永不修改**；承兑记录保留其额度归属的信用证版本号。

#### 修订对既有交单的影响（要点）

| 交单时点/处置 | 审核条款依据 | 金额额度 |
| --- | --- | --- |
| 修订前已承兑 | 不变，承兑台账与归属版本永久保留 | 已计入占用，撤销时恢复 |
| 未承兑 + `KEEP_OLD_VERSION` | 始终按**旧版本**有效期/单据清单补交、差异处理与承兑 | 统一占用当前最高金额信封；承兑时按实时余额校验 |
| 未承兑 + `WITHDRAW_AND_RESUBMIT` | 生效即转 `WITHDRAWN`，不能补交/承兑/登记差异 | 不占用额度；用新交单号按新版本重新交单 |
| 修订生效后的新交单 | 按**新版本**条款审核 | 统一占用当前最高金额信封 |

> 金额是**信用证级统一信封**：无论交单绑定哪个版本，所有承兑共享
> “当前最高金额 − 全版本未撤销承兑累计”的可用余额；版本号只决定
> 有效期与单据清单等条款适用，并作为承兑与流水的归属留痕。
> 因此修订降额后，保留在旧版本上的交单承兑时同样受新最高金额约束。

## 查询接口

- `GET /api/credits/{creditNo}/balance`：余额（当前最高/已承兑/可用金额、当前版本号、乐观锁版本）。
- `GET /api/credits/{creditNo}/versions`：**信用证全部版本及条款差异**，附归属各版本的承兑累计。
- `GET /api/credits/{creditNo}/amendments`：**全部修订申请**（含已接受/拒绝/取消）与受益人决定。
- `GET /api/amendments/{amendmentNo}`：单笔修订（冻结快照、受影响交单、决定、取消信息）。
- `GET /api/credits/{creditNo}/balance-movements`：**余额变化流水**（承兑/撤销/修订结转）。
- `GET /api/presentations/{presentationNo}` 与 `.../versions`：交单（含**所依据信用证版本号**）与全部审核版本。
- `GET /api/presentations/{presentationNo}/discrepancy-decisions`：差异接受决定。
- `GET /api/credits/{creditNo}/acceptances?status=ACCEPTED|REVERSED`：承兑台账（承兑记录携带信用证版本号）。
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

# 交单（绑定当前版本 1，立即形成审核版本 1）
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

# 提出修订（存在未承兑交单且字段变化时必须给 pendingPolicy）
POST /api/credits/LC-001/amendments
{
  "amendmentNo": "AMD-0001",
  "proposedMaxAmount": 1200.0000,
  "proposedExpiryDate": "2027-06-30",
  "proposedAllowedDocumentTypes": ["INVOICE", "PACKING_LIST", "BILL_OF_LADING"],
  "pendingPolicy": "KEEP_OLD_VERSION",
  "proposedBy": "applicant-1"
}

# 受益人接受（目标内容与受影响字段必须与修订完全一致；eventNo 幂等）
POST /api/amendments/AMD-0001/decisions
{
  "eventNo": "EVT-0001",
  "accepted": true,
  "targetMaxAmount": 1200.0000,
  "targetExpiryDate": "2027-06-30",
  "targetAllowedDocumentTypes": ["INVOICE", "PACKING_LIST", "BILL_OF_LADING"],
  "acceptedFields": ["ALLOWED_DOCUMENT_TYPES", "EXPIRY_DATE", "MAX_AMOUNT"],
  "decidedBy": "beneficiary-1"
}

# 受益人拒绝（版本不变，申请与决定保留）
POST /api/amendments/AMD-0001/decisions
{"eventNo": "EVT-0002", "accepted": false, "decidedBy": "beneficiary-1", "reason": "不同意"}

# 申请人在决定前取消（版本不变，申请保留）
POST /api/amendments/AMD-0001/cancel
{"cancelledBy": "applicant-1", "reason": "商务计划调整"}

# 修订生效后的承兑：旧交单仍按旧版本条款，承兑占用统一金额信封
POST /api/presentations/EXT-20260927-001/acceptance
{"amount": 250.0000, "acceptedBy": "officer-1"}
```

## 错误响应

统一返回 `{ "code", "message", "timestamp" }`：资源不存在 404；并发/状态冲突
（重复交单号、版本过期、重复撤销、活动修订冲突、修订期间交单被拦、交单已撤回等）409；
业务前置条件不满足（未接受差异、接受范围不一致、降额低于承兑下限、
未明确未承兑交单归属等）422；参数校验失败 400。

## 测试

- `ReviewEngineTest`：审核规则纯单元测试（清单外单据、缺少单据、效期、超额）。
- `LetterCreditWorkflowTest`：服务层端到端规则测试，覆盖部分承兑、累计余额上限、
  差异精确匹配、补交新版本、版本过期失败、交单号幂等、**多线程并发承兑不超额**、
  撤销恢复余额与台账过滤等 17 个场景。
- `AmendmentWorkflowTest`：修订服务层 20 个场景——提出冻结快照、修订号/事件号幂等、
  活动修订唯一、无变化/降额下限/未承兑交单归属校验、接受范围完全一致、拒绝与取消留痕、
  旧版本交单按旧条款处理、撤回交单终态、**生效时锁内重新校验**、
  **修订生效与承兑/撤销并发不超额**、版本差异/决定/归属/余额流水查询。
- `LetterCreditApiTest` / `AmendmentApiTest`：MockMvc HTTP 全流程测试
  （无差异流程 / 差异-补交-撤销流程 / 修订接受-拒绝-撤回流程 / 404 / 409 / 422）。
