package com.chris64233.lettercredit.domain;

/**
 * 信用证版本的产生方式。
 */
public enum CreditVersionKind {

    /** 开证时生成的初始版本（版本号 1）。 */
    INITIAL,

    /** 修订经受益人接受生效后生成的版本。 */
    AMENDED
}
