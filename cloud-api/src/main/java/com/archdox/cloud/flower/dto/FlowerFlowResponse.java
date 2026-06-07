package com.archdox.cloud.flower.dto;

import java.util.List;

public record FlowerFlowResponse(
        String flowType,
        String flowKey,
        String state,
        String currentStepId,
        int currentStepIndex,
        int currentStepNo,
        String failureType,
        String failureMessage,
        FlowerExecutionContextResponse executionContext,
        List<FlowerFlowStepResponse> steps
) {
    public FlowerFlowResponse {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
