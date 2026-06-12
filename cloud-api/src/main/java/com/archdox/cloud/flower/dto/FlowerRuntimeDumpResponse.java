package com.archdox.cloud.flower.dto;

import com.archdox.cloud.operation.dto.OperationEventResponse;
import java.time.OffsetDateTime;
import java.util.List;

public record FlowerRuntimeDumpResponse(
        String engineState,
        OffsetDateTime capturedAt,
        int workerCount,
        int activeFlowCount,
        int executorCount,
        int saturatedExecutorCount,
        int queuedTaskCount,
        List<FlowerWorkerResponse> workers,
        List<FlowerExecutorResponse> executors,
        List<OperationEventResponse> overloadEvents
) {
    public FlowerRuntimeDumpResponse {
        workers = workers == null ? List.of() : List.copyOf(workers);
        executors = executors == null ? List.of() : List.copyOf(executors);
        overloadEvents = overloadEvents == null ? List.of() : List.copyOf(overloadEvents);
    }
}
