package com.chris64233.lettercredit.exception;

/**
 * 业务错误码，由全局异常处理器映射为 HTTP 状态。
 */
public enum ErrorCode {

    CREDIT_NOT_FOUND,
    PRESENTATION_NOT_FOUND,
    VERSION_NOT_FOUND,
    ACCEPTANCE_NOT_FOUND,
    AMENDMENT_NOT_FOUND,
    AMENDMENT_DECISION_NOT_FOUND,

    /** 交单金额超过信用证当前可用余额。 */
    AVAILABLE_BALANCE_INSUFFICIENT,

    /** 外部交单号重复。 */
    DUPLICATE_PRESENTATION_NO,

    /** 交单已承兑，不允许补交单据。 */
    PRESENTATION_ALREADY_ACCEPTED,

    /** 交单已随修订撤回，不能继续处理。 */
    PRESENTATION_WITHDRAWN,

    /** 承兑请求指定的审核版本不是当前最新版本。 */
    REVIEW_VERSION_STALE,

    /** 承兑请求基于的信用证余额版本已过期。 */
    CREDIT_VERSION_STALE,

    /** 当前审核版本存在差异，但尚无申请人对该版本差异的接受决定。 */
    DISCREPANCY_DECISION_REQUIRED,

    /** 申请人接受的差异范围与当前差异版本不完全一致。 */
    DISCREPANCY_SCOPE_MISMATCH,

    /** 无差异版本不允许登记差异接受决定。 */
    NO_DISCREPANCY_TO_ACCEPT,

    /** 该审核版本的差异接受决定已存在，且内容不一致。 */
    DISCREPANCY_DECISION_CONFLICT,

    /** 承兑已撤销，不能重复撤销。 */
    ACCEPTANCE_ALREADY_REVERSED,

    /** 承兑金额必须为正，且不超过交单金额。 */
    INVALID_ACCEPTANCE_AMOUNT,

    /** 同一信用证已存在一笔活动修订（PROPOSED）。 */
    ACTIVE_AMENDMENT_EXISTS,

    /** 信用证存在活动修订（版本冻结中），暂不受理新交单，待修订终态后再交单。 */
    AMENDMENT_PENDING_PRESENTATION_BLOCKED,

    /** 修订已处于终态（接受/拒绝/取消），不能再登记决定或取消。 */
    AMENDMENT_NOT_ACTIVE,

    /** 修订申请未改变任何字段。 */
    AMENDMENT_NO_CHANGE,

    /** 修订降低后的最高金额低于已承兑累计金额。 */
    AMENDMENT_AMOUNT_BELOW_ACCEPTED,

    /** 修订影响既有未承兑交单所依据字段，但未明确其归属处置方式。 */
    PENDING_PRESENTATION_POLICY_REQUIRED,

    /** 修订未影响既有未承兑交单（或不存在未承兑交单），却指定了处置方式。 */
    PENDING_PRESENTATION_POLICY_NOT_APPLICABLE,

    /** 受益人接受范围与当前修订版本不完全一致。 */
    AMENDMENT_SCOPE_MISMATCH,

    /** 受益人决定（接受/拒绝）与同事件号既有决定不一致。 */
    AMENDMENT_DECISION_CONFLICT,

    /** 请求参数取值非法。 */
    INVALID_REQUEST_PARAM
}
