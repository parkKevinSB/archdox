package com.archdox.cloud.configuration.dto;

import com.archdox.cloud.configuration.domain.ConfigRevisionStatus;
import java.time.OffsetDateTime;
import java.util.Map;

public record DocumentTemplateRevisionResponse(
        Long id,
        Long templateId,
        Integer version,
        ConfigRevisionStatus status,
        String templateStorageKind,
        String templateStorageRef,
        Map<String, Object> schema,
        Map<String, Object> composePolicy,
        Map<String, Object> aiPrompts,
        Long createdBy,
        Long publishedBy,
        OffsetDateTime createdAt,
        OffsetDateTime publishedAt
) {
}
