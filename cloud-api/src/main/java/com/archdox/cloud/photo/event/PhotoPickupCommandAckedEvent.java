package com.archdox.cloud.photo.event;

import java.time.OffsetDateTime;

public record PhotoPickupCommandAckedEvent(
        Long officeId,
        Long photoId,
        Long commandId,
        OffsetDateTime occurredAt
) {
}
