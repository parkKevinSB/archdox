package com.archdox.cloud.document.event;

import java.time.OffsetDateTime;
import java.util.Map;

public record DocumentRenderCommandCompletedEvent(
        Long officeId,
        Long documentJobId,
        Long commandId,
        Map<String, Object> result,
        OffsetDateTime occurredAt
) {
}
