package com.archdox.cloud.photo.event;

import java.time.OffsetDateTime;

public record PhotoOriginalPickupFailed(
        Long officeId,
        Long photoId,
        String reason,
        int attempt,
        OffsetDateTime occurredAt
) {
}
