package com.archdox.cloud.document.dto;

import com.archdox.cloud.document.domain.DocumentDeliveryChannel;

public record CreateDocumentDeliveryRequest(
        Long artifactId,
        DocumentDeliveryChannel channel,
        String recipientRef
) {
    public DocumentDeliveryChannel normalizedChannel() {
        return channel == null ? DocumentDeliveryChannel.DOWNLOAD : channel;
    }
}
