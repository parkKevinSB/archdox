package com.archdox.cloud.flower.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record FlowerRuntimeDumpResponse(
        String engineState,
        OffsetDateTime capturedAt,
        int workerCount,
        int activeFlowCount,
        List<FlowerWorkerResponse> workers
) {
    public FlowerRuntimeDumpResponse {
        workers = workers == null ? List.of() : List.copyOf(workers);
    }
}
