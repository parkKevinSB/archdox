package com.archdox.cloud.document.event;

import java.time.OffsetDateTime;
import java.util.Map;

public record DocumentDeliveryCommandFailedEvent(
        Long officeId,
        Long deliveryRequestId,
        Long commandId,
        String errorMessage,
        Map<String, Object> result,
        OffsetDateTime occurredAt
) {
}
