package com.archdox.cloud.photo.event;

import java.time.OffsetDateTime;
import java.util.Map;

public record PhotoPickupCommandCompletedEvent(
        Long officeId,
        Long photoId,
        Long commandId,
        Map<String, Object> result,
        OffsetDateTime occurredAt
) {
}
