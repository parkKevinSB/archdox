package com.archdox.cloud.photo.event;

import java.time.OffsetDateTime;

public record PhotoDerivativeGenerationFailed(
        Long officeId,
        Long photoId,
        String stepId,
        int attempt,
        String reason,
        OffsetDateTime occurredAt
) {
}
