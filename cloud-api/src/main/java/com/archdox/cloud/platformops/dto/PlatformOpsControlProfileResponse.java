package com.archdox.cloud.platformops.dto;

import com.archdox.cloud.platformops.domain.PlatformOpsControlProfile;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PlatformOpsControlProfileResponse(
        Long id,
        String signalKind,
        String scopeType,
        String modelId,
        String signalKey,
        String signalText,
        String severity,
        BigDecimal iWeight,
        int hitCount,
        Long sourceDailyReportId,
        String notes,
        String status,
        Long createdByUserId,
        Long updatedByUserId,
        OffsetDateTime firstObservedAt,
        OffsetDateTime lastObservedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static PlatformOpsControlProfileResponse from(PlatformOpsControlProfile profile) {
        return new PlatformOpsControlProfileResponse(
                profile.id(),
                profile.signalKind().name(),
                profile.scopeType().name(),
                profile.modelId(),
                profile.signalKey(),
                profile.signalText(),
                profile.severity().name(),
                profile.iWeight(),
                profile.hitCount(),
                profile.sourceDailyReportId(),
                profile.notes(),
                profile.status().name(),
                profile.createdByUserId(),
                profile.updatedByUserId(),
                profile.firstObservedAt(),
                profile.lastObservedAt(),
                profile.createdAt(),
                profile.updatedAt());
    }
}
