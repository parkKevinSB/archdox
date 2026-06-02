package com.archdox.worker.domain;

import java.util.Map;
import java.util.Objects;

public record ArchDoxWorkerAction(
        ArchDoxWorkerActionType actionType,
        Map<String, Object> payload,
        String reason,
        double confidence,
        ArchDoxWorkerActionOrigin origin
) {
    public ArchDoxWorkerAction {
        Objects.requireNonNull(actionType, "actionType must not be null");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        reason = reason == null ? "" : reason.trim();
        if (confidence < 0.0d || confidence > 1.0d) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        origin = origin == null ? ArchDoxWorkerActionOrigin.SYSTEM : origin;
    }

    public static ArchDoxWorkerAction userRequested(ArchDoxWorkerActionType actionType, String reason) {
        return new ArchDoxWorkerAction(actionType, Map.of(), reason, 1.0d, ArchDoxWorkerActionOrigin.USER);
    }
}
