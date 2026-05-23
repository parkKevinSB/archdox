package com.archdox.cloud.document.event;

import java.time.OffsetDateTime;

public record DocumentDeliveryCommandAckedEvent(
        Long officeId,
        Long deliveryRequestId,
        Long commandId,
        OffsetDateTime occurredAt
) {
}
