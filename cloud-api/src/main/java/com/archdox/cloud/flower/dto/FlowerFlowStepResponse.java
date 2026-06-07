package com.archdox.cloud.flower.dto;

public record FlowerFlowStepResponse(
        int index,
        String stepId,
        String stepType,
        boolean current,
        boolean guarded,
        boolean recoverable,
        String recoveryPolicy
) {
}
