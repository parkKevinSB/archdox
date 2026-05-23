package com.archdox.cloud.configuration.dto;

import com.archdox.cloud.configuration.domain.OfficeConfigOverrideStatus;
import java.time.OffsetDateTime;

public record OfficeConfigOverrideResponse(
        Long id,
        Long officeId,
        String reportType,
        OfficeConfigOverrideStatus status,
        ResolvedConfigPartResponse template,
        ResolvedConfigPartResponse workflow,
        ResolvedConfigPartResponse ruleSet,
        ResolvedConfigPartResponse outputLayout,
        OffsetDateTime effectiveFrom,
        OffsetDateTime effectiveTo,
        Long createdBy,
        Long updatedBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
