package com.archdox.cloud.document.event;

import java.time.OffsetDateTime;
import java.util.List;

public record DocumentGeneratedEvent(
        Long officeId,
        Long reportId,
        Long documentJobId,
        List<Long> artifactIds,
        OffsetDateTime occurredAt
) {
}
