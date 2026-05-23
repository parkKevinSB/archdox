package com.archdox.cloud.document.event;

import java.time.OffsetDateTime;

public record DocumentGenerationFailedEvent(
        Long officeId,
        Long reportId,
        Long documentJobId,
        String stepId,
        int attempt,
        String reason,
        OffsetDateTime occurredAt
) {
}
