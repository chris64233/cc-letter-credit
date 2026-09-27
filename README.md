# cc-letter-credit

信用证与交单资料管理服务。

当前提供跟单信用证的开立、交单审核、差异处理、部分承兑与撤销能力，以及对应的查询接口。

## 开发环境

- JDK 21
- Maven Wrapper 3.9.9
- Spring Boot 4.1.1

迁移项目沿用现有 Spring Boot 版本，其他项目使用上述版本。

## 主要业务规则

### 信用证

- 信用证记录受益人、币种、最高金额、有效期和允许的单据类型。
- 可用金额初始等于最高金额，随承兑扣减、随撤销恢复；累计承兑金额不得超过信用证余额。

### 交单与审核

- 一次交单包含外部交单号、金额和多份单据摘要；同一信用证下外部交单号唯一，作为幂等键：重复提交（金额一致）返回原交单，金额不一致则冲突。
- 每次提交/补交都会生成一个审核版本，差异清单随版本固化、不可修改；补交单据产生新版本，旧版本继续保留。
- 审核差异规则：信用证已过期（`LC_EXPIRED`）、交单金额超过当前可用余额（`AMOUNT_EXCEEDS_AVAILABLE`）、单据类型不在允许清单内（`DOCUMENT_TYPE_NOT_ALLOWED:<类型>`）、单据类型缺失（`DOCUMENT_TYPE_MISSING`）。

### 差异处理与承兑

- 无差异交单可直接承兑；有差异交单只有在申请人明确接受指定差异后才能承兑，且接受范围必须与当前差异版本完全一致（不多不少）。
- 承兑请求必须携带审核版本号；审核结果发生变化（如补交产生新版本）后，基于旧版本的承兑失败。
- 承兑在同一事务中扣减信用证可用金额并记录承兑结果；信用证余额使用乐观锁，并发承兑不会超额，基于旧余额版本的承兑失败。
- 承兑幂等：同一交单按同一版本重复承兑返回原承兑记录，余额只扣减一次。
- 已承兑交单不得再补交单据或作差异决定。

### 撤销

- 承兑记录一经创建不得修改；只能通过独立的撤销决定恢复信用证余额。
- 撤销决定必须记录完整原因和处理人；一笔承兑至多撤销一次，台账中承兑与撤销记录均保留。

### 查询接口

- `GET /api/letter-credits/{id}` — 信用证余额查询
- `GET /api/presentations/{id}/versions` — 交单版本（含每次审核的差异清单）
- `GET /api/presentations/{id}/discrepancy-decisions` — 差异决定查询
- `GET /api/letter-credits/{id}/acceptances` — 承兑台账（承兑记录及其撤销决定）

### 主要写接口

- `POST /api/letter-credits` — 开立信用证
- `POST /api/letter-credits/{lcId}/presentations` — 提交交单（生成首个审核版本）
- `POST /api/presentations/{id}/supplements` — 补交单据（生成新审核版本）
- `POST /api/presentations/{id}/discrepancy-decisions` — 申请人接受差异
- `POST /api/presentations/{id}/acceptances` — 承兑（携带审核版本号）
- `POST /api/acceptances/{id}/cancellations` — 撤销承兑

错误映射：资源不存在 404，状态冲突（版本过期、余额不足、差异未接受、重复操作等）409，请求内容不合法 400。

## 常用命令

运行测试：

    ./mvnw clean test

启动服务：

    ./mvnw spring-boot:run
