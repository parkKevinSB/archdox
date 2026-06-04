package com.archdox.cloud.legal.dto;

import com.archdox.cloud.legal.domain.LegalSyncRunStatus;
import java.time.OffsetDateTime;
import java.util.Map;

public record LegalSyncRunResponse(
        Long id,
        String triggerType,
        String sourceCode,
        LegalSyncRunStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String failureCode,
        Map<String, Object> summary
) {
}
