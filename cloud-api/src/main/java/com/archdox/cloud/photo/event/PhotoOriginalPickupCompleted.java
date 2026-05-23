package com.archdox.cloud.photo.event;

import java.time.OffsetDateTime;

public record PhotoOriginalPickupCompleted(
        Long officeId,
        Long photoId,
        Long commandId,
        OffsetDateTime occurredAt
) {
}
