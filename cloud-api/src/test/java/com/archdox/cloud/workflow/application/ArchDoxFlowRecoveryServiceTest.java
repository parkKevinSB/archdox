package com.archdox.cloud.workflow.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.agent.application.ArchDoxAgentCommandService;
import com.archdox.cloud.document.application.DocumentDeliveryProperties;
import com.archdox.cloud.document.application.DocumentDeliveryService;
import com.archdox.cloud.document.application.DocumentGenerationProperties;
import com.archdox.cloud.document.application.DocumentJobService;
import com.archdox.cloud.document.domain.DocumentDeliveryChannel;
import com.archdox.cloud.document.domain.DocumentDeliveryRequest;
import com.archdox.cloud.document.domain.DocumentDeliveryStatus;
import com.archdox.cloud.document.domain.DocumentJob;
import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.document.domain.DocumentWorkerType;
import com.archdox.cloud.document.flow.DocumentDeliveryFlowFactory;
import com.archdox.cloud.document.flow.DocumentDeliveryWorker;
import com.archdox.cloud.document.flow.DocumentGenerationFlowFactory;
import com.archdox.cloud.document.flow.DocumentGenerationWorker;
import com.archdox.cloud.document.infra.DocumentDeliveryRequestRepository;
import com.archdox.cloud.document.infra.DocumentJobRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.photo.application.PhotoPickupProperties;
import com.archdox.cloud.photo.application.PhotoPickupService;
import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoCaptureKind;
import com.archdox.cloud.photo.domain.PhotoPickupStatus;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import com.archdox.cloud.photo.domain.PhotoUploadTarget;
import com.archdox.cloud.photo.flow.PhotoPickupFlowFactory;
import com.archdox.cloud.photo.flow.PhotoPickupWorker;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.document.OutputFormat;
import io.github.parkkevinsb.flower.core.flow.Flow;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ArchDoxFlowRecoveryServiceTest {
    @Test
    void recoversIncompleteBusinessStateIntoFlowerFlows() {
        var commandService = mock(ArchDoxAgentCommandService.class);
        var photoRepository = mock(PhotoRepository.class);
        var photoPickupWorker = mock(PhotoPickupWorker.class);
        var documentJobRepository = mock(DocumentJobRepository.class);
        var documentGenerationWorker = mock(DocumentGenerationWorker.class);
        var deliveryRepository = mock(DocumentDeliveryRequestRepository.class);
        var deliveryWorker = mock(DocumentDeliveryWorker.class);
        var now = OffsetDateTime.now();

        when(commandService.expireInFlightCommandsForRuntimeRecovery()).thenReturn(2);
        when(photoRepository.findByStatusAndOriginalPickupStatusAndUploadTargetOrderByConfirmedAtAsc(
                PhotoStatus.UPLOADED,
                PhotoPickupStatus.PENDING,
                PhotoUploadTarget.CLOUD_MEDIATED))
                .thenReturn(List.of(photo(now)));
        when(documentJobRepository.findByStatusInOrderByRequestedAtAsc(List.of(
                DocumentJobStatus.REQUESTED,
                DocumentJobStatus.GENERATING)))
                .thenReturn(List.of(documentJob(now)));
        when(deliveryRepository.findByStatusOrderByRequestedAtAsc(DocumentDeliveryStatus.SENDING))
                .thenReturn(List.of(delivery(now)));

        var service = new ArchDoxFlowRecoveryService(
                mock(JdbcTemplate.class),
                commandService,
                photoRepository,
                new PhotoPickupFlowFactory(
                        mock(PhotoPickupService.class),
                        commandService,
                        new PhotoPickupProperties()),
                photoPickupWorker,
                documentJobRepository,
                new DocumentGenerationFlowFactory(
                        mock(DocumentJobService.class),
                        commandService,
                        Runnable::run,
                        new DocumentGenerationProperties()),
                documentGenerationWorker,
                deliveryRepository,
                new DocumentDeliveryFlowFactory(
                        mock(DocumentDeliveryService.class),
                        commandService,
                        new DocumentDeliveryProperties()),
                deliveryWorker,
                mock(OperationEventService.class));

        var result = service.recoverDatabaseState();

        assertEquals(2, result.expiredCommands());
        assertEquals(1, result.photoPickups());
        assertEquals(1, result.documentJobs());
        assertEquals(1, result.documentDeliveries());
        verify(photoPickupWorker).submit(any(Flow.class));
        verify(documentGenerationWorker).submit(any(Flow.class));
        verify(deliveryWorker).submit(any(Flow.class));
    }

    private Photo photo(OffsetDateTime now) {
        var photo = new Photo(
                10L,
                100L,
                1000L,
                "STEP_1",
                null,
                PhotoCaptureKind.UPLOAD,
                "image/jpeg",
                100L,
                "hash",
                PhotoStorageKind.S3,
                "photos/original.jpg",
                "photos/thumb.webp",
                PhotoUploadTarget.CLOUD_MEDIATED,
                1L,
                now,
                null,
                null,
                now);
        photo.confirm(100L, 100, 100, 1L, now);
        return photo;
    }

    private DocumentJob documentJob(OffsetDateTime now) {
        return new DocumentJob(
                10L,
                1000L,
                100L,
                1,
                null,
                1L,
                DocumentWorkerType.CLOUD,
                OutputFormat.DOCX,
                Map.of(),
                now);
    }

    private DocumentDeliveryRequest delivery(OffsetDateTime now) {
        var delivery = new DocumentDeliveryRequest(
                10L,
                700L,
                900L,
                DocumentDeliveryChannel.DOWNLOAD,
                null,
                1L,
                now);
        delivery.markSending(now);
        return delivery;
    }
}
