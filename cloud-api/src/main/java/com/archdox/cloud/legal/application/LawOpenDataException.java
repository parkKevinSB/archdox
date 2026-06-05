package com.archdox.cloud.legal.application;

public class LawOpenDataException extends RuntimeException {
    private final String code;
    private final String endpoint;
    private final String target;
    private final int statusCode;
    private final boolean retryable;

    public LawOpenDataException(
            String code,
            String endpoint,
            String target,
            int statusCode,
            boolean retryable,
            String message
    ) {
        this(code, endpoint, target, statusCode, retryable, message, null);
    }

    public LawOpenDataException(
            String code,
            String endpoint,
            String target,
            int statusCode,
            boolean retryable,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.code = blankToDefault(code, "LAW_OPEN_DATA_ERROR");
        this.endpoint = blankToDefault(endpoint, "");
        this.target = blankToDefault(target, "");
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public String endpoint() {
        return endpoint;
    }

    public String target() {
        return target;
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean retryable() {
        return retryable;
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
