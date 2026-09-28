package com.chris64233.lettercredit.domain;

/**
 * 修订（信用证修改申请）的生命周期状态。
 *
 * <p>只有 {@link #PROPOSED} 是活动状态：同一信用证同时至多存在一笔活动修订；
 * 受益人接受即转为 {@link #EFFECTIVE} 并生成新信用证版本；受益人拒绝或
 * 申请人取消分别转为 {@link #REJECTED} / {@link #CANCELLED}，申请与决定
 * 均原样保留，但不改变当前信用证版本。</p>
 */
public enum AmendmentStatus {

    /** 已提出，等待受益人决定（唯一的活动状态）。 */
    PROPOSED,

    /** 受益人已接受，修订生效并生成新信用证版本。 */
    EFFECTIVE,

    /** 受益人拒绝，修订留痕但不生效。 */
    REJECTED,

    /** 申请人在受益人决定前取消，修订留痕但不生效。 */
    CANCELLED;

    public boolean isActive() {
        return this == PROPOSED;
    }
}
