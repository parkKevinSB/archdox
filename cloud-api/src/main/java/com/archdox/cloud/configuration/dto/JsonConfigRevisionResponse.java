package com.archdox.cloud.configuration.dto;

import com.archdox.cloud.configuration.domain.ConfigRevisionStatus;
import java.time.OffsetDateTime;
import java.util.Map;

public record JsonConfigRevisionResponse(
        Long id,
        Long definitionId,
        Integer version,
        ConfigRevisionStatus status,
        Map<String, Object> payload,
        Long createdBy,
        Long publishedBy,
        OffsetDateTime createdAt,
        OffsetDateTime publishedAt
) {
}
