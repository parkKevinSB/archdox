package com.archdox.cloud.worker.governance.dto;

public record WorkerGovernanceGroupResponse(
        String actionType,
        String eventType,
        String reasonCode,
        long count
) {
}
