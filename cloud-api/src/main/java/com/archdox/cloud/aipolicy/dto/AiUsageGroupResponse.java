package com.archdox.cloud.aipolicy.dto;

import java.math.BigDecimal;

public record AiUsageGroupResponse(
        Long officeId,
        String feature,
        long callCount,
        long succeededCount,
        long failedCount,
        long inputTokens,
        long outputTokens,
        BigDecimal estimatedTotalCost
) {
}
