package com.archdox.cloud.configuration.dto;

import com.archdox.cloud.configuration.domain.ConfigDefinitionStatus;
import java.time.OffsetDateTime;

public record ConfigDefinitionResponse(
        Long id,
        Long officeId,
        String code,
        String name,
        String reportType,
        ConfigDefinitionStatus status,
        Long createdBy,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
