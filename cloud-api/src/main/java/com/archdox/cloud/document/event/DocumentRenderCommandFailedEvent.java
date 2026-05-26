package com.archdox.cloud.document.event;

import java.time.OffsetDateTime;
import java.util.Map;

public record DocumentRenderCommandFailedEvent(
        Long officeId,
        Long documentJobId,
        Long commandId,
        String errorCode,
        Boolean retryable,
        String errorMessage,
        Map<String, Object> result,
        OffsetDateTime occurredAt
) {
    public DocumentRenderCommandFailedEvent(
            Long officeId,
            Long documentJobId,
            Long commandId,
            String errorMessage,
            Map<String, Object> result,
            OffsetDateTime occurredAt
    ) {
        this(officeId, documentJobId, commandId, null, null, errorMessage, result, occurredAt);
    }
}
