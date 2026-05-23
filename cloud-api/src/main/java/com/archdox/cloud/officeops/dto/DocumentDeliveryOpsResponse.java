package com.archdox.cloud.officeops.dto;

import com.archdox.cloud.document.domain.DocumentDeliveryChannel;
import com.archdox.cloud.document.domain.DocumentDeliveryStatus;
import java.time.OffsetDateTime;

public record DocumentDeliveryOpsResponse(
        Long id,
        Long officeId,
        Long documentJobId,
        Long artifactId,
        DocumentDeliveryChannel channel,
        DocumentDeliveryStatus status,
        String recipientRef,
        Long requestedBy,
        String errorMessage,
        String preparedStorageKind,
        OffsetDateTime preparedExpiresAt,
        OffsetDateTime downloadReadyAt,
        Long agentCommandId,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt,
        OffsetDateTime updatedAt
) {
}
