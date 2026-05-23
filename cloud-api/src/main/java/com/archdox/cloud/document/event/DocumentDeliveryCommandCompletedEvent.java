package com.archdox.cloud.document.event;

import java.time.OffsetDateTime;
import java.util.Map;

public record DocumentDeliveryCommandCompletedEvent(
        Long officeId,
        Long deliveryRequestId,
        Long commandId,
        Map<String, Object> result,
        OffsetDateTime occurredAt
) {
}
