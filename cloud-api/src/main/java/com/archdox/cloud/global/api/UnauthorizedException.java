package com.archdox.cloud.global.api;

import java.util.Map;

public class UnauthorizedException extends RuntimeException implements ApiException {
    private final String code;
    private final String messageKey;
    private final Map<String, Object> params;

    public UnauthorizedException(String message) {
        this("UNAUTHORIZED", "errors.unauthorized", message, Map.of());
    }

    public UnauthorizedException(String code, String messageKey, String message) {
        this(code, messageKey, message, Map.of());
    }

    public UnauthorizedException(String code, String messageKey, String message, Map<String, Object> params) {
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
