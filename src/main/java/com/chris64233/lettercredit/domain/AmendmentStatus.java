package com.chris64233.lettercredit.domain;

/**
 * 信用证修订（修改）的生命周期状态。
 */
public enum AmendmentStatus {

    /** 已提出，等待受益人决定（同一信用证同时至多一笔处于该状态的修订）。 */
    PROPOSED,

    /** 受益人接受并已生效，新信用证版本已生成，终态。 */
    ACCEPTED,

    /** 受益人拒绝，申请与决定保留，信用证版本不变，终态。 */
    REJECTED,

    /** 申请人在受益人决定前取消，申请保留，信用证版本不变，终态。 */
    CANCELLED
}
