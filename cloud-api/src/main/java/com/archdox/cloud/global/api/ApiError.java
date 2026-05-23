package com.archdox.cloud.global.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ApiError(
        int status,
        String code,
        String messageKey,
        String message,
        Map<String, Object> params,
        List<ApiFieldError> fieldErrors,
        String requestId,
        OffsetDateTime timestamp
) {
    public static ApiError of(int status, String code, String messageKey, String message) {
        return of(status, code, messageKey, message, Map.of(), List.of());
    }

    public static ApiError of(
            int status,
            String code,
            String messageKey,
            String message,
            Map<String, Object> params,
            List<ApiFieldError> fieldErrors
    ) {
        return new ApiError(
                status,
                code,
                messageKey,
                message,
                params == null ? Map.of() : Map.copyOf(params),
                fieldErrors == null ? List.of() : List.copyOf(fieldErrors),
                UUID.randomUUID().toString(),
                OffsetDateTime.now());
    }
}
