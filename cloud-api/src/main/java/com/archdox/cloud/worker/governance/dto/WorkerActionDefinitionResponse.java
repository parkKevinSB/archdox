package com.archdox.cloud.worker.governance.dto;

import java.util.List;

public record WorkerActionDefinitionResponse(
        String actionType,
        String owner,
        String executorName,
        boolean enabled,
        boolean executorRegistered,
        boolean readOnly,
        String riskLevel,
        boolean requiresApprovalByDefault,
        boolean supportsDryRun,
        List<String> allowedSources,
        List<String> requiredContextFields,
        String description
) {
}
