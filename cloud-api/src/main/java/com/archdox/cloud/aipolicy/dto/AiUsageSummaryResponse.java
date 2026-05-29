package com.archdox.cloud.aipolicy.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AiUsageSummaryResponse(
        OffsetDateTime periodFrom,
        OffsetDateTime periodTo,
        String currency,
        long callCount,
        long succeededCount,
        long failedCount,
        long inputTokens,
        long outputTokens,
        BigDecimal estimatedTotalCost,
        List<AiUsageGroupResponse> groups
) {
}
