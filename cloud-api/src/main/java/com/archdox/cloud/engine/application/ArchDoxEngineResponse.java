package com.archdox.cloud.engine.application;

import java.util.List;
import java.util.Map;

public record ArchDoxEngineResponse(
        String engineRunId,
        ArchDoxEngineResultStatus status,
        boolean generationAllowed,
        List<ArchDoxEngineFinding> findings,
        List<String> nextActions,
        Map<String, Object> metadata
) {
    public ArchDoxEngineResponse {
        findings = findings == null ? List.of() : List.copyOf(findings);
        nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
