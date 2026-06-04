package com.archdox.cloud.worker.engine;

import com.archdox.worker.domain.ArchDoxWorkerAction;
import java.util.Map;

public record EngineWorkerActionCandidate(
        String suggestedAction,
        boolean known,
        boolean enabled,
        boolean executorRegistered,
        ArchDoxWorkerAction action,
        Map<String, Object> definitionMetadata,
        String reason
) {
    public EngineWorkerActionCandidate {
        suggestedAction = suggestedAction == null ? "" : suggestedAction.trim();
        definitionMetadata = definitionMetadata == null ? Map.of() : Map.copyOf(definitionMetadata);
        reason = reason == null ? "" : reason.trim();
    }

    public Map<String, Object> toMetadata() {
        return Map.of(
                "suggestedAction", suggestedAction,
                "known", known,
                "enabled", enabled,
                "executorRegistered", executorRegistered,
                "actionType", action == null ? "" : action.actionType().name(),
                "reason", reason,
                "definition", definitionMetadata);
    }
}
