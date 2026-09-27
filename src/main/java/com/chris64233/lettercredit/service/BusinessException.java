package com.chris64233.lettercredit.service;

/**
 * 业务异常基类。
 */
public abstract class BusinessException extends RuntimeException {

    protected BusinessException(String message) {
        super(message);
    }
}
