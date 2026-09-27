package com.chris64233.lettercredit.domain;

/**
 * 交单生命周期状态。
 */
public enum PresentationStatus {

    /** 已交单，尚未承兑。 */
    PRESENTED,

    /** 已承兑（可能已撤销）。 */
    ACCEPTED
}
