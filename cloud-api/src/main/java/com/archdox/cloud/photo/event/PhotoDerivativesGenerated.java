package com.archdox.cloud.photo.event;

import java.time.OffsetDateTime;

public record PhotoDerivativesGenerated(
        Long officeId,
        Long photoId,
        OffsetDateTime occurredAt
) {
}
