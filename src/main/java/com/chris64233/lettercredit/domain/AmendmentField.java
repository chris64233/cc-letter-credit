package com.chris64233.lettercredit.domain;

/**
 * 修订字段的稳定标识键，用于受益人接受范围与当前修订内容的精确比对
 * （与差异键的模式一致）。
 */
public final class AmendmentField {

    /** 最高金额发生变化。 */
    public static final String MAX_AMOUNT = "MAX_AMOUNT";

    /** 有效期发生变化。 */
    public static final String EXPIRY_DATE = "EXPIRY_DATE";

    /** 允许单据类型清单发生变化。 */
    public static final String ALLOWED_DOCUMENT_TYPES = "ALLOWED_DOCUMENT_TYPES";

    private AmendmentField() {
    }
}
