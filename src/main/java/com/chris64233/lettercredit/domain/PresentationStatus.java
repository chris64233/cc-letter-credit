package com.chris64233.lettercredit.domain;

/**
 * 交单生命周期状态。
 */
public enum PresentationStatus {

    /** 已交单，尚未承兑。 */
    PRESENTED,

    /** 已承兑（可能已撤销）。 */
    ACCEPTED,

    /**
     * 已随信用证修订生效被撤回（申请人选择“撤回后按新版本补交”）。
     * 终态：不能补交、承兑或登记差异决定；只能按新版本重新交单。
     */
    WITHDRAWN
}
