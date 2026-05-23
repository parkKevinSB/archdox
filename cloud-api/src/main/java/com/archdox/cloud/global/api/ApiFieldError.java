package com.archdox.cloud.global.api;

import java.util.Map;

public record ApiFieldError(
        String field,
        String code,
        String message,
        Map<String, Object> params
) {
    public static ApiFieldError of(String field, String code, String message) {
        return new ApiFieldError(field, code, message, Map.of());
    }
}
