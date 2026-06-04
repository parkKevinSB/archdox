package com.archdox.cloud.engine.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ArchDoxEngineService {
    public ArchDoxEngineResponse prepare(ArchDoxEngineRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        var engineRunId = "eng_" + UUID.randomUUID();
        return new ArchDoxEngineResponse(
                engineRunId,
                ArchDoxEngineResultStatus.PENDING,
                false,
                List.of(),
                List.of("SUBMIT_CONTEXT", "NORMALIZE_CONTEXT", "RUN_ENGINE_RECIPE_VALIDATION"),
                Map.of(
                        "phase", "ENGINE_BOUNDARY_PREPARE",
                        "boundary", "ARCHDOX_ENGINE_BOUNDARY",
                        "message", "Engine boundary prepared. Worker governance is not executed by the Engine.",
                        "engineBoundaryRole", "CONTEXT_NORMALIZATION_AND_RECIPE_REVIEW",
                        "governanceBoundary", "ARCHDOX_WORKER_SERVICE",
                        "workerGovernanceExecuted", false,
                        "requestedCapabilities", request.capabilities().stream().map(Enum::name).sorted().toList()));
    }
}
