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
     * 已撤回：修订生效且受益人选择「撤回后按新版本补交」时，
     * 基线版本下尚未承兑的交单进入此状态，不能再补交或承兑，需重新交单。
     */
    WITHDRAWN
}
