package com.archdox.worker.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record ArchDoxWorkerActionDefinition(
        ArchDoxWorkerActionType actionType,
        String owner,
        String executorName,
        boolean enabled,
        boolean readOnly,
        ArchDoxWorkerActionRiskLevel riskLevel,
        boolean requiresApprovalByDefault,
        boolean supportsDryRun,
        Set<ArchDoxWorkerRequestSource> allowedSources,
        Set<String> requiredContextFields,
        String description
) {
    public ArchDoxWorkerActionDefinition {
        actionType = actionType == null ? ArchDoxWorkerActionType.WORKER_CHAT_ADVANCE : actionType;
        owner = normalize(owner);
        executorName = normalize(executorName);
        riskLevel = riskLevel == null ? ArchDoxWorkerActionRiskLevel.MEDIUM : riskLevel;
        allowedSources = allowedSources == null ? Set.of() : Set.copyOf(allowedSources);
        requiredContextFields = requiredContextFields == null ? Set.of() : Set.copyOf(requiredContextFields);
        description = normalize(description);
    }

    public boolean allowsSource(ArchDoxWorkerRequestSource source) {
        return allowedSources.isEmpty() || allowedSources.contains(source);
    }

    public Map<String, Object> toMetadata() {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("actionType", actionType.name());
        metadata.put("owner", owner);
        metadata.put("executorName", executorName);
        metadata.put("enabled", enabled);
        metadata.put("readOnly", readOnly);
        metadata.put("riskLevel", riskLevel.name());
        metadata.put("requiresApprovalByDefault", requiresApprovalByDefault);
        metadata.put("supportsDryRun", supportsDryRun);
        metadata.put("allowedSources", allowedSources.stream().map(Enum::name).sorted().toList());
        metadata.put("requiredContextFields", requiredContextFields.stream().sorted().toList());
        metadata.put("description", description);
        return Map.copyOf(metadata);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
