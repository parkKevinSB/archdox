package com.archdox.cloud.configuration.dto;

import java.time.OffsetDateTime;

public record UpdateOfficeConfigOverrideRequest(
        Long templateRevisionId,
        Long workflowRevisionId,
        Long ruleSetRevisionId,
        Long outputLayoutRevisionId,
        OffsetDateTime effectiveFrom,
        OffsetDateTime effectiveTo
) {
}
