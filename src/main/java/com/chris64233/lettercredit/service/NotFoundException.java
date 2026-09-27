package com.chris64233.lettercredit.service;

/**
 * 资源不存在（HTTP 404）。
 */
public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(message);
    }
}
