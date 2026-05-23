package com.archdox.cloud.document.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class DocumentDeliveryRequestWorkflowTest {
    @Test
    void agentBackedDownloadMovesFromSendingToCompletedWhenPrepared() {
        var now = OffsetDateTime.now();
        var delivery = new DocumentDeliveryRequest(
                10L,
                700L,
                900L,
                DocumentDeliveryChannel.DOWNLOAD,
                null,
                20L,
                now);

        delivery.markSending(55L, now.plusSeconds(1));
        delivery.markPrepared(
                DocumentArtifactStorageKind.API_LOCAL.name(),
                "deliveries/1/report.docx",
                now.plusHours(1),
                now.plusSeconds(2));

        assertEquals(DocumentDeliveryStatus.COMPLETED, delivery.status());
        assertEquals(55L, delivery.agentCommandId());
        assertEquals("API_LOCAL", delivery.preparedStorageKind());
        assertEquals("deliveries/1/report.docx", delivery.preparedStorageRef());
        assertNotNull(delivery.downloadReadyAt());
        assertNotNull(delivery.completedAt());
    }

    @Test
    void completedDeliveryIsNotDowngradedByLateFailure() {
        var now = OffsetDateTime.now();
        var delivery = new DocumentDeliveryRequest(
                10L,
                700L,
                900L,
                DocumentDeliveryChannel.DOWNLOAD,
                null,
                20L,
                now);
        delivery.markPrepared("API_LOCAL", "deliveries/1/report.docx", now.plusHours(1), now.plusSeconds(1));

        delivery.markFailed("late timeout", now.plusSeconds(2));

        assertEquals(DocumentDeliveryStatus.COMPLETED, delivery.status());
    }
}
