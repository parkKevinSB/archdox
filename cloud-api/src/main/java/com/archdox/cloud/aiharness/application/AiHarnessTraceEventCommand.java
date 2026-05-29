package com.archdox.cloud.aiharness.application;

import java.util.Map;

public record AiHarnessTraceEventCommand(
        Long officeId,
        String harnessRunId,
        String harnessId,
        String eventType,
        String status,
        Integer attempt,
        String modelId,
        String callId,
        String promptId,
        String promptVersion,
        Integer inputTokens,
        Integer outputTokens,
        Long latencyMs,
        Integer findingCount,
        Boolean validationValid,
        Integer validationErrorCount,
        String errorType,
        String message,
        Map<String, Object> attributes
) {
    public AiHarnessTraceEventCommand {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
