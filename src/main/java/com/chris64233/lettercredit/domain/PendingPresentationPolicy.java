package com.chris64233.lettercredit.domain;

/**
 * 修订生效时，对修订提出时已存在的<strong>未承兑交单</strong>的处置方式。
 *
 * <p>仅当修订改变了这些交单所依据的字段（最高金额、有效期或允许单据类型）时，
 * 申请人才必须明确选择处置方式。</p>
 */
public enum PendingPresentationPolicy {

    /** 交单继续按修订前的旧信用证版本审核与承兑，金额归属旧版本。 */
    KEEP_OLD_VERSION,

    /** 交单随修订生效被撤回，之后只能按新版本重新交单。 */
    WITHDRAW_AND_RESUBMIT
}
