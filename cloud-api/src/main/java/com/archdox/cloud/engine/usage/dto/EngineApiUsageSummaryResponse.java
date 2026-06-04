package com.archdox.cloud.engine.usage.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record EngineApiUsageSummaryResponse(
        OffsetDateTime from,
        OffsetDateTime to,
        long totalEventCount,
        long totalRequestUnits,
        List<EngineApiUsageGroupResponse> groups
) {
    public EngineApiUsageSummaryResponse {
        groups = groups == null ? List.of() : List.copyOf(groups);
    }
}
