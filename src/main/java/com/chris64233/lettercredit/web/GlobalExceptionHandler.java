package com.chris64233.lettercredit.web;

import com.chris64233.lettercredit.exception.BusinessException;
import com.chris64233.lettercredit.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Set<ErrorCode> NOT_FOUND = Set.of(
            ErrorCode.CREDIT_NOT_FOUND,
            ErrorCode.PRESENTATION_NOT_FOUND,
            ErrorCode.VERSION_NOT_FOUND,
            ErrorCode.ACCEPTANCE_NOT_FOUND);

    private static final Set<ErrorCode> BAD_REQUEST = Set.of(
            ErrorCode.INVALID_REQUEST_PARAM,
            ErrorCode.INVALID_ACCEPTANCE_AMOUNT);

    private static final Set<ErrorCode> CONFLICT = Set.of(
            ErrorCode.DUPLICATE_PRESENTATION_NO,
            ErrorCode.PRESENTATION_ALREADY_ACCEPTED,
            ErrorCode.ACCEPTANCE_ALREADY_REVERSED,
            ErrorCode.REVIEW_VERSION_STALE,
            ErrorCode.CREDIT_VERSION_STALE,
            ErrorCode.DISCREPANCY_DECISION_CONFLICT);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        HttpStatus status = NOT_FOUND.contains(ex.getErrorCode()) ? HttpStatus.NOT_FOUND
                : BAD_REQUEST.contains(ex.getErrorCode()) ? HttpStatus.BAD_REQUEST
                : CONFLICT.contains(ex.getErrorCode()) ? HttpStatus.CONFLICT
                : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(body(ex.getErrorCode().name(), ex.getMessage()));
    }

    /** 极端并发下唯一约束兜底：交单号/承兑号重复按冲突处理。 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(body("DATA_INTEGRITY_VIOLATION", "唯一约束冲突，资源可能已存在"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        Map<String, Object> body = body("VALIDATION_FAILED", "请求参数校验失败");
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    private Map<String, Object> body(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("timestamp", OffsetDateTime.now().toString());
        return body;
    }
}
