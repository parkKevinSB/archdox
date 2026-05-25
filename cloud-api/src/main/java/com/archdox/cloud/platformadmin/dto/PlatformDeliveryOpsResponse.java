package com.archdox.cloud.platformadmin.dto;

import com.archdox.cloud.document.domain.DocumentDeliveryChannel;
import com.archdox.cloud.document.domain.DocumentDeliveryStatus;
import java.time.OffsetDateTime;

public record PlatformDeliveryOpsResponse(
        Long id,
        Long officeId,
        Long documentJobId,
        Long artifactId,
        DocumentDeliveryChannel channel,
        DocumentDeliveryStatus status,
        Long agentCommandId,
        String errorMessage,
        OffsetDateTime requestedAt,
        OffsetDateTime updatedAt
) {
}
