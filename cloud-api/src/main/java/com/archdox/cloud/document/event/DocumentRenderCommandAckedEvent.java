package com.archdox.cloud.document.event;

import java.time.OffsetDateTime;

public record DocumentRenderCommandAckedEvent(
        Long officeId,
        Long documentJobId,
        Long commandId,
        OffsetDateTime occurredAt
) {
}
