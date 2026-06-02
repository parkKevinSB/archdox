package com.archdox.workerai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ConversationPlannerResult(
        ConversationPlannerDecision decision,
        String actionType,
        boolean requiresConfirmation,
        double confidence,
        String userMessage,
        Map<String, Object> payload,
        String rationale
) {
    public ConversationPlannerResult {
        decision = Objects.requireNonNull(decision, "decision must not be null");
        actionType = actionType == null ? "" : actionType.trim().toUpperCase();
        userMessage = Objects.toString(userMessage, "").trim();
        rationale = Objects.toString(rationale, "").trim();
        payload = payload == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(payload));
        if (confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        if (decision == ConversationPlannerDecision.PROPOSE_ACTION && actionType.isBlank()) {
            throw new IllegalArgumentException("PROPOSE_ACTION requires actionType");
        }
    }
}
