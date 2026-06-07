package com.rodminjo.commerce.common.infra.web;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response body for all services. Success responses are asymmetric — they return the
 * resource directly (200/201/204), only errors are wrapped in this envelope.
 *
 * @param code        machine-readable error code ({@code ErrorCode.code()})
 * @param message     human-readable message
 * @param fieldErrors per-field validation errors; empty list when not a validation error (never null)
 * @param timestamp   when the error occurred (from {@code ClockHolder})
 * @param path        request path
 * @param traceId     correlation id shared with the server log
 */
public record ErrorResponse(
        String code,
        String message,
        List<FieldError> fieldErrors,
        Instant timestamp,
        String path,
        String traceId
) {
    public record FieldError(String field, String reason) {}
}
