package com.archdox.cloud.flower.dto;

import java.util.List;

public record FlowerWorkerResponse(
        String name,
        String state,
        long intervalMillis,
        int activeFlowCount,
        List<FlowerFlowResponse> flows
) {
    public FlowerWorkerResponse {
        flows = flows == null ? List.of() : List.copyOf(flows);
    }
}
