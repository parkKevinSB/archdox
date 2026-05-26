package com.archdox.agent.cloud;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AgentCommandFailureClassifier {
    private static final Pattern HTTP_STATUS = Pattern.compile("HTTP\\s+(\\d{3})", Pattern.CASE_INSENSITIVE);

    private AgentCommandFailureClassifier() {
    }

    public static AgentCommandFailure classify(Throwable cause, String defaultErrorCode) {
        var message = messageOf(cause);
        var normalized = message.toLowerCase(Locale.ROOT);
        var httpStatus = httpStatus(message);
        if (httpStatus == 401 || httpStatus == 403) {
            return failure("AGENT_AUTH_FAILED", false, message);
        }
        if (httpStatus == 404) {
            return failure("AGENT_REMOTE_RESOURCE_NOT_FOUND", false, message);
        }
        if (httpStatus == 408 || httpStatus == 429 || httpStatus >= 500) {
            return failure("AGENT_REMOTE_SERVICE_UNAVAILABLE", true, message);
        }
        if (normalized.contains("interrupted")) {
            return failure("AGENT_COMMAND_INTERRUPTED", true, message);
        }
        if (normalized.contains("unsupported")) {
            return failure("AGENT_UNSUPPORTED_COMMAND_PAYLOAD", false, message);
        }
        if (normalized.contains(" is required")) {
            return failure("AGENT_INVALID_COMMAND_PAYLOAD", false, message);
        }
        if (normalized.contains("template content could not be read")
                || normalized.contains("zipfile invalid")
                || normalized.contains("bad signature")) {
            return failure("TEMPLATE_INVALID_DOCX", false, message);
        }
        if (normalized.contains("libreoffice executable is not available")
                || normalized.contains("libreoffice export is disabled")
                || (normalized.contains("soffice") && normalized.contains("not available"))) {
            return failure("PDF_CONVERTER_UNAVAILABLE", false, message);
        }
        if (normalized.contains("libreoffice pdf export timed out")) {
            return failure("PDF_CONVERTER_TIMEOUT", true, message);
        }
        if (normalized.contains("timed out") || normalized.contains("timeout")) {
            return failure("AGENT_COMMAND_TIMEOUT", true, message);
        }
        if (normalized.contains("storage is not configured")
                || normalized.contains("root path is required")) {
            return failure("AGENT_STORAGE_NOT_CONFIGURED", false, message);
        }
        if (normalized.contains("hash does not match")) {
            return failure("AGENT_CONTENT_HASH_MISMATCH", false, message);
        }
        if (cause instanceof IOException) {
            return failure("AGENT_IO_TRANSIENT_FAILURE", true, message);
        }
        if (cause instanceof IllegalArgumentException) {
            return failure("AGENT_INVALID_COMMAND_PAYLOAD", false, message);
        }
        return failure(defaultErrorCode, false, message);
    }

    private static AgentCommandFailure failure(String errorCode, boolean retryable, String message) {
        return new AgentCommandFailure(errorCode, retryable, message);
    }

    private static String messageOf(Throwable cause) {
        if (cause == null || cause.getMessage() == null || cause.getMessage().isBlank()) {
            return "Agent command failed";
        }
        return cause.getMessage();
    }

    private static int httpStatus(String message) {
        var matcher = HTTP_STATUS.matcher(message);
        if (!matcher.find()) {
            return -1;
        }
        return Integer.parseInt(matcher.group(1));
    }
}
