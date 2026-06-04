package com.archdox.cloud.worker.engine;

import com.archdox.cloud.engine.application.EngineValidationResult;
import com.archdox.worker.application.ArchDoxWorkerActionRegistry;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionOrigin;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class EngineWorkerActionSuggestionBridge {
    private static final String SUGGESTED_WORKER_ACTIONS = "suggestedWorkerActions";

    private final ObjectProvider<ArchDoxWorkerActionRegistry> registryProvider;

    public EngineWorkerActionSuggestionBridge(ObjectProvider<ArchDoxWorkerActionRegistry> registryProvider) {
        this.registryProvider = registryProvider;
    }

    public List<EngineWorkerActionCandidate> candidates(
            EngineValidationResult engineResult,
            Map<String, Object> basePayload
    ) {
        if (engineResult == null) {
            return List.of();
        }
        return suggestedActions(engineResult).stream()
                .map(suggestion -> candidate(engineResult, suggestion, basePayload))
                .toList();
    }

    public List<Map<String, Object>> candidateMetadata(
            EngineValidationResult engineResult,
            Map<String, Object> basePayload
    ) {
        return candidates(engineResult, basePayload).stream()
                .map(EngineWorkerActionCandidate::toMetadata)
                .toList();
    }

    private EngineWorkerActionCandidate candidate(
            EngineValidationResult engineResult,
            String suggestion,
            Map<String, Object> basePayload
    ) {
        var actionType = actionType(suggestion);
        if (actionType == null) {
            return new EngineWorkerActionCandidate(
                    suggestion,
                    false,
                    false,
                    false,
                    null,
                    Map.of(),
                    "Engine suggested an action name that is not an ArchDoxWorkerActionType.");
        }
        var registry = registryProvider.getIfAvailable();
        if (registry == null) {
            return new EngineWorkerActionCandidate(
                    suggestion,
                    false,
                    false,
                    false,
                    null,
                    Map.of(),
                    "Worker action registry is not available.");
        }
        var definition = registry.definition(actionType).orElse(null);
        if (definition == null) {
            return new EngineWorkerActionCandidate(
                    suggestion,
                    false,
                    false,
                    false,
                    null,
                    Map.of(),
                    "Worker action definition is missing.");
        }
        var executorRegistered = registry.resolve(actionType).isPresent();
        var action = new ArchDoxWorkerAction(
                actionType,
                payload(engineResult, basePayload),
                "Suggested by ArchDox Engine recipe validation.",
                confidence(engineResult),
                ArchDoxWorkerActionOrigin.PLANNER);
        return new EngineWorkerActionCandidate(
                suggestion,
                true,
                definition.enabled(),
                executorRegistered,
                action,
                definition.toMetadata(),
                executorRegistered
                        ? "Candidate only. Execution must still pass Worker resolve, policy, and Flower flow."
                        : "Candidate only. No Worker executor is registered yet.");
    }

    @SuppressWarnings("unchecked")
    private List<String> suggestedActions(EngineValidationResult engineResult) {
        var raw = engineResult.metadata().get(SUGGESTED_WORKER_ACTIONS);
        if (!(raw instanceof List<?> suggestions)) {
            return List.of();
        }
        return suggestions.stream()
                .map(this::text)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private Map<String, Object> payload(
            EngineValidationResult engineResult,
            Map<String, Object> basePayload
    ) {
        var payload = new LinkedHashMap<String, Object>();
        if (basePayload != null) {
            payload.putAll(basePayload);
        }
        payload.put("engineRunId", engineResult.engineRunId());
        payload.put("engineStatus", engineResult.status().name());
        payload.put("enginePhase", engineResult.enginePhase());
        payload.put("engineFindingCodes", engineResult.findings().stream()
                .map(finding -> finding.code())
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList());
        payload.put("engineNextActions", engineResult.nextActions());
        return Map.copyOf(payload);
    }

    private ArchDoxWorkerActionType actionType(String suggestion) {
        if (suggestion == null || suggestion.isBlank()) {
            return null;
        }
        try {
            return ArchDoxWorkerActionType.valueOf(suggestion.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private double confidence(EngineValidationResult engineResult) {
        return switch (engineResult.status()) {
            case PASS -> 0.8d;
            case WARN -> 0.65d;
            case FAIL -> 0.5d;
            default -> 0.4d;
        };
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
