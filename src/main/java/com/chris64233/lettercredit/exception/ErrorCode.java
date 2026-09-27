package com.chris64233.lettercredit.exception;

/**
 * 业务错误码，由全局异常处理器映射为 HTTP 状态。
 */
public enum ErrorCode {

    CREDIT_NOT_FOUND,
    PRESENTATION_NOT_FOUND,
    VERSION_NOT_FOUND,
    ACCEPTANCE_NOT_FOUND,

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

    /** 请求参数取值非法。 */
    INVALID_REQUEST_PARAM
}
