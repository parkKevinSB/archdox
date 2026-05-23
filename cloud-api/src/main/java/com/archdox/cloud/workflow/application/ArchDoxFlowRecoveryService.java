package com.archdox.cloud.workflow.application;

import com.archdox.cloud.agent.application.ArchDoxAgentCommandService;
import com.archdox.cloud.document.domain.DocumentDeliveryStatus;
import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.document.event.DocumentDeliveryRequested;
import com.archdox.cloud.document.event.DocumentGenerationRequested;
import com.archdox.cloud.document.flow.DocumentDeliveryFlowFactory;
import com.archdox.cloud.document.flow.DocumentDeliveryWorker;
import com.archdox.cloud.document.flow.DocumentGenerationFlowFactory;
import com.archdox.cloud.document.flow.DocumentGenerationWorker;
import com.archdox.cloud.document.infra.DocumentDeliveryRequestRepository;
import com.archdox.cloud.document.infra.DocumentJobRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.photo.domain.PhotoPickupStatus;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.domain.PhotoUploadTarget;
import com.archdox.cloud.photo.event.PhotoPickupRequested;
import com.archdox.cloud.photo.flow.PhotoPickupFlowFactory;
import com.archdox.cloud.photo.flow.PhotoPickupWorker;
import com.archdox.cloud.photo.infra.PhotoRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ArchDoxFlowRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(ArchDoxFlowRecoveryService.class);
    private static final long FLOW_RECOVERY_LOCK_KEY = 4_103_407_004_700_007L;

    private final JdbcTemplate jdbcTemplate;
    private final ArchDoxAgentCommandService commandService;
    private final PhotoRepository photoRepository;
    private final PhotoPickupFlowFactory photoPickupFlowFactory;
    private final PhotoPickupWorker photoPickupWorker;
    private final DocumentJobRepository documentJobRepository;
    private final DocumentGenerationFlowFactory documentGenerationFlowFactory;
    private final DocumentGenerationWorker documentGenerationWorker;
    private final DocumentDeliveryRequestRepository deliveryRequestRepository;
    private final DocumentDeliveryFlowFactory deliveryFlowFactory;
    private final DocumentDeliveryWorker deliveryWorker;
    private final OperationEventService operationEventService;

    public ArchDoxFlowRecoveryService(
            JdbcTemplate jdbcTemplate,
            ArchDoxAgentCommandService commandService,
            PhotoRepository photoRepository,
            PhotoPickupFlowFactory photoPickupFlowFactory,
            PhotoPickupWorker photoPickupWorker,
            DocumentJobRepository documentJobRepository,
            DocumentGenerationFlowFactory documentGenerationFlowFactory,
            DocumentGenerationWorker documentGenerationWorker,
            DocumentDeliveryRequestRepository deliveryRequestRepository,
            DocumentDeliveryFlowFactory deliveryFlowFactory,
            DocumentDeliveryWorker deliveryWorker,
            OperationEventService operationEventService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.commandService = commandService;
        this.photoRepository = photoRepository;
        this.photoPickupFlowFactory = photoPickupFlowFactory;
        this.photoPickupWorker = photoPickupWorker;
        this.documentJobRepository = documentJobRepository;
        this.documentGenerationFlowFactory = documentGenerationFlowFactory;
        this.documentGenerationWorker = documentGenerationWorker;
        this.deliveryRequestRepository = deliveryRequestRepository;
        this.deliveryFlowFactory = deliveryFlowFactory;
        this.deliveryWorker = deliveryWorker;
        this.operationEventService = operationEventService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            if (!tryLock(connection)) {
                log.info("Skipping ArchDox flow recovery because another Cloud API instance owns the recovery lock");
                return null;
            }
            try {
                var result = recoverDatabaseState();
                log.info(
                        "Recovered ArchDox flows: expiredCommands={}, photoPickups={}, documentJobs={}, documentDeliveries={}",
                        result.expiredCommands(),
                        result.photoPickups(),
                        result.documentJobs(),
                        result.documentDeliveries());
            } finally {
                unlock(connection);
            }
            return null;
        });
    }

    public FlowRecoveryResult recoverDatabaseState() {
        var expiredCommands = commandService.expireInFlightCommandsForRuntimeRecovery();
        var recoveredPhotos = recoverPhotoPickupFlows();
        var recoveredDocumentJobs = recoverDocumentGenerationFlows();
        var recoveredDeliveries = recoverDocumentDeliveryFlows();
        var result = new FlowRecoveryResult(
                expiredCommands,
                recoveredPhotos,
                recoveredDocumentJobs,
                recoveredDeliveries);
        operationEventService.record(
                null,
                OperationEventSeverity.INFO,
                "FLOW_RECOVERY_COMPLETED",
                "flow-recovery",
                "cloud-api-startup",
                "CLOUD_API",
                null,
                "Recovered workflow flows from durable DB state.",
                Map.of(
                        "expiredCommands", expiredCommands,
                        "photoPickups", recoveredPhotos,
                        "documentJobs", recoveredDocumentJobs,
                        "documentDeliveries", recoveredDeliveries));
        return result;
    }

    private int recoverPhotoPickupFlows() {
        var photos = photoRepository.findByStatusAndOriginalPickupStatusAndUploadTargetOrderByConfirmedAtAsc(
                PhotoStatus.UPLOADED,
                PhotoPickupStatus.PENDING,
                PhotoUploadTarget.CLOUD_MEDIATED);
        photos.forEach(photo -> photoPickupWorker.submit(photoPickupFlowFactory.create(new PhotoPickupRequested(
                photo.officeId(),
                photo.id(),
                photo.reportId(),
                photo.projectId(),
                OffsetDateTime.now()))));
        return photos.size();
    }

    private int recoverDocumentGenerationFlows() {
        var jobs = documentJobRepository.findByStatusInOrderByRequestedAtAsc(List.of(
                DocumentJobStatus.REQUESTED,
                DocumentJobStatus.GENERATING));
        jobs.forEach(job -> documentGenerationWorker.submit(documentGenerationFlowFactory.create(new DocumentGenerationRequested(
                job.officeId(),
                job.reportId(),
                job.id(),
                job.workerType(),
                OffsetDateTime.now()))));
        return jobs.size();
    }

    private int recoverDocumentDeliveryFlows() {
        var deliveries = deliveryRequestRepository.findByStatusOrderByRequestedAtAsc(DocumentDeliveryStatus.SENDING);
        deliveries.forEach(delivery -> deliveryWorker.submit(deliveryFlowFactory.create(new DocumentDeliveryRequested(
                delivery.officeId(),
                delivery.documentJobId(),
                delivery.id(),
                delivery.artifactId(),
                OffsetDateTime.now()))));
        return deliveries.size();
    }

    private boolean tryLock(java.sql.Connection connection) throws java.sql.SQLException {
        try (var statement = connection.prepareStatement("select pg_try_advisory_lock(?)")) {
            statement.setLong(1, FLOW_RECOVERY_LOCK_KEY);
            try (var result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private void unlock(java.sql.Connection connection) throws java.sql.SQLException {
        try (var statement = connection.prepareStatement("select pg_advisory_unlock(?)")) {
            statement.setLong(1, FLOW_RECOVERY_LOCK_KEY);
            statement.execute();
        }
    }

    public record FlowRecoveryResult(
            int expiredCommands,
            int photoPickups,
            int documentJobs,
            int documentDeliveries
    ) {
    }
}
