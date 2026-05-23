package com.archdox.cloud.global.api;

import java.util.Map;

public class ForbiddenException extends RuntimeException implements ApiException {
    private final String code;
    private final String messageKey;
    private final Map<String, Object> params;

    public ForbiddenException(String message) {
        this("FORBIDDEN", "errors.forbidden", message, Map.of());
    }

    public ForbiddenException(String code, String messageKey, String message) {
        this(code, messageKey, message, Map.of());
    }

    public ForbiddenException(String code, String messageKey, String message, Map<String, Object> params) {
        super(message);
        this.code = code;
        this.messageKey = messageKey;
        this.params = Map.copyOf(params);
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }

    @Override
    public Map<String, Object> params() {
        return params;
    }
}
