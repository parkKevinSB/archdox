package com.archdox.cloud.engine.dto;

import com.archdox.cloud.engine.application.ArchDoxEngineResultStatus;
import java.util.List;
import java.util.Map;

public record EngineValidationResultResponse(
        String engineRunId,
        ArchDoxEngineResultStatus status,
        boolean generationAllowed,
        String summary,
        List<EngineFindingResponse> findings,
        List<EngineLegalReferenceResponse> legalReferences,
        List<EngineNextActionResponse> nextActions,
        String policyDecision,
        List<String> executedActions,
        String enginePhase,
        Map<String, Object> metadata
) {
    public EngineValidationResultResponse {
        engineRunId = engineRunId == null ? "" : engineRunId.trim();
        status = status == null ? ArchDoxEngineResultStatus.PENDING : status;
        summary = summary == null ? "" : summary.trim();
        findings = findings == null ? List.of() : List.copyOf(findings);
        legalReferences = legalReferences == null ? List.of() : List.copyOf(legalReferences);
        nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
        policyDecision = policyDecision == null ? "" : policyDecision.trim();
        executedActions = executedActions == null ? List.of() : List.copyOf(executedActions);
        enginePhase = enginePhase == null ? "" : enginePhase.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static EngineValidationResultResponse empty() {
        return new EngineValidationResultResponse(
                "",
                ArchDoxEngineResultStatus.PENDING,
                false,
                "",
                List.of(),
                List.of(),
                List.of(),
                "",
                List.of(),
                "",
                Map.of());
    }
}
