package com.chris64233.lettercredit.service;

/**
 * 状态冲突：版本过期、余额不足、重复承兑、差异未被接受等（HTTP 409）。
 */
public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(message);
    }
}
