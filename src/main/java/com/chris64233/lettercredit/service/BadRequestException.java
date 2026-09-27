package com.chris64233.lettercredit.service;

/**
 * 请求内容不合法（HTTP 400）。
 */
public class BadRequestException extends BusinessException {

    public BadRequestException(String message) {
        super(message);
    }
}
