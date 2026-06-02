package com.archdox.worker.domain;

public record ArchDoxWorkerPolicyDecision(
        ArchDoxWorkerPolicyDecisionType type,
        String reasonCode,
        String message
) {
    public ArchDoxWorkerPolicyDecision {
        type = type == null ? ArchDoxWorkerPolicyDecisionType.DENY : type;
        reasonCode = reasonCode == null ? "" : reasonCode.trim();
        message = message == null ? "" : message.trim();
    }

    public boolean allowed() {
        return type != ArchDoxWorkerPolicyDecisionType.DENY;
    }

    public boolean requiresApproval() {
        return type == ArchDoxWorkerPolicyDecisionType.REQUIRE_APPROVAL;
    }

    public static ArchDoxWorkerPolicyDecision allow() {
        return new ArchDoxWorkerPolicyDecision(ArchDoxWorkerPolicyDecisionType.ALLOW, "ALLOW", "Allowed");
    }

    public static ArchDoxWorkerPolicyDecision requireApproval(String reasonCode, String message) {
        return new ArchDoxWorkerPolicyDecision(ArchDoxWorkerPolicyDecisionType.REQUIRE_APPROVAL, reasonCode, message);
    }

    public static ArchDoxWorkerPolicyDecision deny(String reasonCode, String message) {
        return new ArchDoxWorkerPolicyDecision(ArchDoxWorkerPolicyDecisionType.DENY, reasonCode, message);
    }
}
