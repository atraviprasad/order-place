package com.orderplace.common.web;

import org.springframework.http.HttpStatus;

/** Throw from services; mapped to the RFC 7807 style error format by GlobalExceptionHandler. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }

    public static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message); }
    public static ApiException notFound(String message) { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message); }
    public static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    public static ApiException unprocessable(String code, String message) { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message); }
}
