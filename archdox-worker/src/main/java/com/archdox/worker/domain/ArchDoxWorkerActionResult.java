package com.archdox.worker.domain;

import java.util.Map;

public record ArchDoxWorkerActionResult(
        ArchDoxWorkerActionExecutionStatus status,
        String resultCode,
        String message,
        Map<String, Object> output
) {
    public ArchDoxWorkerActionResult {
        status = status == null ? ArchDoxWorkerActionExecutionStatus.FAILED : status;
        resultCode = resultCode == null ? "" : resultCode.trim();
        message = message == null ? "" : message.trim();
        output = output == null ? Map.of() : Map.copyOf(output);
    }

    public static ArchDoxWorkerActionResult succeeded(Map<String, Object> output) {
        return new ArchDoxWorkerActionResult(ArchDoxWorkerActionExecutionStatus.SUCCEEDED, "SUCCEEDED", "Succeeded", output);
    }

    public static ArchDoxWorkerActionResult pendingApproval(String resultCode, String message) {
        return new ArchDoxWorkerActionResult(ArchDoxWorkerActionExecutionStatus.PENDING_APPROVAL, resultCode, message, Map.of());
    }

    public static ArchDoxWorkerActionResult rejected(String resultCode, String message) {
        return new ArchDoxWorkerActionResult(ArchDoxWorkerActionExecutionStatus.REJECTED, resultCode, message, Map.of());
    }

    public static ArchDoxWorkerActionResult cancelled(String resultCode, String message) {
        return new ArchDoxWorkerActionResult(ArchDoxWorkerActionExecutionStatus.CANCELLED, resultCode, message, Map.of());
    }

    public static ArchDoxWorkerActionResult failed(String resultCode, String message) {
        return new ArchDoxWorkerActionResult(ArchDoxWorkerActionExecutionStatus.FAILED, resultCode, message, Map.of());
    }
}
