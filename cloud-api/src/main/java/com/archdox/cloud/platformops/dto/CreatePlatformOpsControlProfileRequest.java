package com.archdox.cloud.platformops.dto;

import java.math.BigDecimal;

public record CreatePlatformOpsControlProfileRequest(
        String signalKind,
        String scopeType,
        String modelId,
        String signalText,
        String severity,
        BigDecimal iWeight,
        Long sourceDailyReportId,
        String notes
) {
}
