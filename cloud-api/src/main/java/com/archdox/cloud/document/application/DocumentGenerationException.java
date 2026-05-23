package com.archdox.cloud.document.application;

public class DocumentGenerationException extends RuntimeException {
    private final String errorCode;

    public DocumentGenerationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DocumentGenerationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
