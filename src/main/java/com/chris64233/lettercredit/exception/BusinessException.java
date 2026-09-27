package com.chris64233.lettercredit.exception;

/**
 * 可预期的业务规则违反。由全局异常处理器转为 4xx 响应。
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
