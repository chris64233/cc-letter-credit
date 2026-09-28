package com.chris64233.lettercredit.domain;

/**
 * 信用证余额变动类型。
 */
public enum BalanceChangeType {

    /** 承兑占用余额。 */
    ACCEPTED,

    /** 撤销承兑恢复余额。 */
    REVERSED,

    /** 修订生效（最高金额变化，可用金额随之变化；已承兑金额不变）。 */
    AMENDMENT_EFFECTIVE
}
