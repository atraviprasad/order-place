package com.orderplace.common.web;

import java.util.List;

public record ErrorResponse(String type, String title, int status, String code, String detail, String traceId, List<FieldError> errors) {
    public record FieldError(String field, String message) {}
}
