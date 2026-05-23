package com.archdox.cloud.document.event;

import com.archdox.cloud.document.domain.DocumentWorkerType;
import java.time.OffsetDateTime;

public record DocumentGenerationRequested(
        Long officeId,
        Long reportId,
        Long documentJobId,
        DocumentWorkerType workerType,
        OffsetDateTime occurredAt
) {
}
