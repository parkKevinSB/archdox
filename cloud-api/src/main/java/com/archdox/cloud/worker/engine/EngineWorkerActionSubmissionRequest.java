package com.archdox.cloud.worker.engine;

import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerRequestContext;
import com.archdox.worker.domain.ArchDoxWorkerRequestSource;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record EngineWorkerActionSubmissionRequest(
        UUID requestId,
        ArchDoxWorkerRequestSource source,
        String command,
        ArchDoxWorkerRequestContext context,
        Map<String, Object> basePayload,
        Set<ArchDoxWorkerActionType> excludedActionTypes
) {
    public EngineWorkerActionSubmissionRequest {
        requestId = requestId == null ? UUID.randomUUID() : requestId;
        source = source == null ? ArchDoxWorkerRequestSource.SYSTEM : source;
        command = command == null || command.isBlank()
                ? "Execute ArchDox Engine suggested Worker action"
                : command.trim();
        context = context == null ? ArchDoxWorkerRequestContext.empty() : context;
        basePayload = basePayload == null ? Map.of() : Map.copyOf(basePayload);
        excludedActionTypes = excludedActionTypes == null ? Set.of() : Set.copyOf(excludedActionTypes);
    }
}
