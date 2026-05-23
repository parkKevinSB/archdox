package com.archdox.cloud.photo.event;

import java.time.OffsetDateTime;
import java.util.Map;

public record PhotoPickupCommandFailedEvent(
        Long officeId,
        Long photoId,
        Long commandId,
        String errorMessage,
        Map<String, Object> result,
        OffsetDateTime occurredAt
) {
}
