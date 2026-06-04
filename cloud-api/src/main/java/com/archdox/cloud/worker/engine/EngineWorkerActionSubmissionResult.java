package com.archdox.cloud.worker.engine;

import java.util.List;
import java.util.Map;

public record EngineWorkerActionSubmissionResult(
        List<Map<String, Object>> candidates,
        List<Map<String, Object>> submitted,
        List<Map<String, Object>> skipped
) {
    public EngineWorkerActionSubmissionResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        submitted = submitted == null ? List.of() : List.copyOf(submitted);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
    }

    public static EngineWorkerActionSubmissionResult empty() {
        return new EngineWorkerActionSubmissionResult(List.of(), List.of(), List.of());
    }

    public Map<String, Object> toMetadata() {
        return Map.of(
                "candidateCount", candidates.size(),
                "submittedCount", submitted.size(),
                "skippedCount", skipped.size(),
                "candidates", candidates,
                "submitted", submitted,
                "skipped", skipped);
    }
}
