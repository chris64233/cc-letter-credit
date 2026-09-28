package com.chris64233.lettercredit.domain;

/**
 * 修订生效时，对修订基线版本下尚未承兑交单的归属策略。
 *
 * <p>修订涉及的三个字段（最高金额、有效期、允许单据类型）都会影响交单审核，
 * 因此只要存在尚未承兑的交单，受益人接受修订时就必须明确其归属：</p>
 * <ul>
 *   <li>{@link #KEEP_OLD_VERSION}：未承兑交单继续按其交单时的旧信用证版本
 *       审核与承兑，不因新版本自动重审；</li>
 *   <li>{@link #WITHDRAW_RESUBMIT}：未承兑交单随修订生效一并撤回，
 *       受益人按新版本重新交单。</li>
 * </ul>
 */
public enum PendingPresentationPolicy {

    /** 未承兑交单继续使用旧版本，直至承兑或另行处理。 */
    KEEP_OLD_VERSION,

    /** 未承兑交单随修订生效撤回，需按新版本重新交单。 */
    WITHDRAW_RESUBMIT
}
