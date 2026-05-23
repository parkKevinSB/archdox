package com.archdox.cloud.document.event;

import java.time.OffsetDateTime;

public record DocumentDeliveryRequested(
        Long officeId,
        Long documentJobId,
        Long deliveryRequestId,
        Long artifactId,
        OffsetDateTime requestedAt
) {
}
