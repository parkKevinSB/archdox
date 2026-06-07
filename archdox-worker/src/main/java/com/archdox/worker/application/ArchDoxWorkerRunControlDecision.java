package com.archdox.worker.application;

public record ArchDoxWorkerRunControlDecision(
        boolean allowed,
        String reasonCode,
        String message
) {
    public ArchDoxWorkerRunControlDecision {
        reasonCode = reasonCode == null ? "" : reasonCode.trim();
        message = message == null ? "" : message.trim();
    }

    public static ArchDoxWorkerRunControlDecision allow() {
        return new ArchDoxWorkerRunControlDecision(true, "ALLOW", "Allowed");
    }

    public static ArchDoxWorkerRunControlDecision cancel(String reasonCode, String message) {
        return new ArchDoxWorkerRunControlDecision(false, reasonCode, message);
    }
}
