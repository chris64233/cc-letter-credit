package com.chris64233.lettercredit.domain;

/**
 * 承兑台账状态。
 */
public enum AcceptanceStatus {

    /** 正常生效，已占用信用证余额。 */
    ACCEPTED,

    /** 已撤销，余额已恢复。 */
    REVERSED
}
