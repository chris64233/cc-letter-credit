package com.chris64233.lettercredit.domain;

/**
 * 信用证版本状态。版本一旦生成即不可修改；新版本生效后旧版本转入
 * {@link #SUPERSEDED}，仅用于历史查询与既有交单/承兑的归属追溯。
 */
public enum CreditVersionStatus {

    /** 当前生效版本。 */
    CURRENT,

    /** 已被后续修订产生的新版本取代，内容不可修改。 */
    SUPERSEDED
}
