package com.archdox.cloud.document.dto;

import com.archdox.cloud.document.domain.DocumentDeliveryChannel;
import com.archdox.cloud.document.domain.DocumentDeliveryStatus;
import java.time.OffsetDateTime;

public record DocumentDeliveryRequestResponse(
        Long id,
        Long officeId,
        Long documentJobId,
        Long artifactId,
        DocumentDeliveryChannel channel,
        DocumentDeliveryStatus status,
        String recipientRef,
        String errorMessage,
        String downloadUrl,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt,
        OffsetDateTime updatedAt
) {
}
