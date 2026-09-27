package com.chris64233.lettercredit.domain;

/**
 * 交单审核差异类型。
 */
public enum DiscrepancyCode {

    /** 单据类型不在信用证允许范围内。 */
    DOC_TYPE_NOT_ALLOWED,

    /** 缺少信用证规定必须提交的单据类型。 */
    MISSING_REQUIRED_DOCUMENT,

    /** 交单时信用证已过有效期。 */
    LC_EXPIRED,

    /** 交单金额超过信用证当前可用余额（也即不可能承兑）。 */
    AMOUNT_EXCEEDS_BALANCE
}
