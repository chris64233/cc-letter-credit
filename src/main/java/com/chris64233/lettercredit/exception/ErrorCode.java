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

    /** 修订没有修改任何条款（三项目标值与当前版本完全相同）。 */
    AMENDMENT_NO_TERMS_CHANGED,

    /** 修订后最高金额低于累计已承兑金额。 */
    AMENDMENT_AMOUNT_BELOW_ACCEPTED,

    /** 修订后最高金额不足以覆盖尚未承兑交单的金额，交单将失去明确归属。 */
    PENDING_PRESENTATIONS_NOT_COVERED,

    /** 受益人接受修订时声明的字段变化集合与修订实际变化不完全一致。 */
    AMENDMENT_SCOPE_MISMATCH,

    /** 存在尚未承兑交单，但受益人未明确其继续使用旧版本或撤回重交。 */
    PENDING_PRESENTATION_POLICY_REQUIRED,

    /** 修订编号重复。 */
    DUPLICATE_AMENDMENT_NO,

    /** 同一信用证已存在一笔活动修订。 */
    ACTIVE_AMENDMENT_EXISTS,

    /** 修订已被接受/拒绝/取消，不再允许决定或取消。 */
    AMENDMENT_NOT_ACTIVE,

    /** 修订基线版本已过期（当前信用证版本与修订基线不一致）。 */
    AMENDMENT_BASE_VERSION_STALE,

    /** 修订已有内容不一致的受益人决定（事件号复用冲突）。 */
    AMENDMENT_DECISION_CONFLICT,

    /** 交单已随修订生效撤回，不能再补交或承兑。 */
    PRESENTATION_WITHDRAWN,

    /** 请求参数取值非法。 */
    INVALID_REQUEST_PARAM
}
