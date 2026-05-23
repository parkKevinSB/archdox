package com.archdox.cloud.document.application;

public record AgentDocumentDeliveryUpload(
        Long deliveryRequestId,
        Long artifactId,
        String preparedStorageKind,
        String preparedStorageRef,
        long bytes,
        String hashSha256
) {
}
