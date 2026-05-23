package com.archdox.cloud.operation.dto;

import com.archdox.cloud.operation.domain.OperationEventSeverity;
import java.time.OffsetDateTime;
import java.util.Map;

public record OperationEventResponse(
        Long id,
        Long officeId,
        OperationEventSeverity severity,
        String eventType,
        String workflowType,
        String workflowKey,
        String resourceType,
        String resourceId,
        Long actorUserId,
        String correlationId,
        String message,
        Map<String, Object> payload,
        OffsetDateTime createdAt
) {
}
