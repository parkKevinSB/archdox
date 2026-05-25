package com.archdox.cloud.platformadmin.application;

import com.archdox.cloud.agent.domain.ArchDoxAgentCommandStatus;
import com.archdox.cloud.agent.infra.ArchDoxAgentCommandRepository;
import com.archdox.cloud.document.domain.DocumentDeliveryStatus;
import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.document.infra.DocumentDeliveryRequestRepository;
import com.archdox.cloud.document.infra.DocumentJobRepository;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.photo.domain.PhotoPickupStatus;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.cloud.platformadmin.dto.PlatformHealthDetectionResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformHealthDetectionService {
    private final PlatformAdminService platformAdminService;
    private final PlatformHealthDetectionProperties properties;
    private final DocumentJobRepository documentJobRepository;
    private final ArchDoxAgentCommandRepository commandRepository;
    private final PhotoRepository photoRepository;
    private final DocumentDeliveryRequestRepository deliveryRepository;
    private final OperationEventService operationEventService;

    public PlatformHealthDetectionService(
            PlatformAdminService platformAdminService,
            PlatformHealthDetectionProperties properties,
            DocumentJobRepository documentJobRepository,
            ArchDoxAgentCommandRepository commandRepository,
            PhotoRepository photoRepository,
            DocumentDeliveryRequestRepository deliveryRepository,
            OperationEventService operationEventService
    ) {
        this.platformAdminService = platformAdminService;
        this.properties = properties;
        this.documentJobRepository = documentJobRepository;
        this.commandRepository = commandRepository;
        this.photoRepository = photoRepository;
        this.deliveryRepository = deliveryRepository;
        this.operationEventService = operationEventService;
    }

    @Transactional
    public PlatformHealthDetectionResponse detectAndRecord(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        var now = OffsetDateTime.now();
        var page = PageRequest.of(0, Math.max(1, properties.getMaxDetectedItems()));

        var jobs = documentJobRepository.findByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                List.of(DocumentJobStatus.REQUESTED, DocumentJobStatus.GENERATING),
                now.minusMinutes(properties.getDocumentJobStuckMinutes()),
                page);
        jobs.forEach(job -> operationEventService.record(
                job.officeId(),
                OperationEventSeverity.WARN,
                "DOCUMENT_JOB_STUCK_DETECTED",
                "document-generation",
                String.valueOf(job.id()),
                "DOCUMENT_JOB",
                job.id(),
                principal.userId(),
                null,
                "Document job appears stuck",
                Map.of(
                        "status", job.status().name(),
                        "progressStep", job.progressStep().name(),
                        "progressPercent", job.progressPercent(),
                        "updatedAt", job.updatedAt().toString())));

        var commands = commandRepository.findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
                List.of(ArchDoxAgentCommandStatus.PENDING, ArchDoxAgentCommandStatus.DELIVERED, ArchDoxAgentCommandStatus.ACKED),
                now.minusMinutes(properties.getAgentCommandStuckMinutes()),
                page);
        commands.forEach(command -> operationEventService.record(
                command.agent().officeId(),
                OperationEventSeverity.WARN,
                "AGENT_COMMAND_STUCK_DETECTED",
                "agent-command",
                String.valueOf(command.id()),
                "AGENT_COMMAND",
                command.id(),
                principal.userId(),
                null,
                "Agent command appears stuck",
                Map.of(
                        "agentId", command.agent().id(),
                        "commandType", command.commandType().name(),
                        "status", command.status().name(),
                        "attemptCount", command.attemptCount(),
                        "createdAt", command.createdAt().toString())));

        var photos = photoRepository.findByStatusAndOriginalPickupStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                PhotoStatus.UPLOADED,
                PhotoPickupStatus.PENDING,
                now.minusMinutes(properties.getPhotoPickupStuckMinutes()),
                page);
        photos.forEach(photo -> operationEventService.record(
                photo.officeId(),
                OperationEventSeverity.WARN,
                "PHOTO_PICKUP_STUCK_DETECTED",
                "photo-pickup",
                String.valueOf(photo.id()),
                "PHOTO",
                photo.id(),
                principal.userId(),
                null,
                "Photo original pickup appears stuck",
                Map.of(
                        "reportId", nullable(photo.reportId()),
                        "uploadTarget", photo.uploadTarget().name(),
                        "updatedAt", photo.updatedAt().toString())));

        var deliveries = deliveryRepository.findByStatusInAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                List.of(DocumentDeliveryStatus.REQUESTED, DocumentDeliveryStatus.SENDING),
                now.minusMinutes(properties.getDeliveryStuckMinutes()),
                page);
        deliveries.forEach(delivery -> operationEventService.record(
                delivery.officeId(),
                OperationEventSeverity.WARN,
                "DOCUMENT_DELIVERY_STUCK_DETECTED",
                "document-delivery",
                String.valueOf(delivery.id()),
                "DOCUMENT_DELIVERY_REQUEST",
                delivery.id(),
                principal.userId(),
                null,
                "Document delivery appears stuck",
                Map.of(
                        "documentJobId", delivery.documentJobId(),
                        "status", delivery.status().name(),
                        "agentCommandId", nullable(delivery.agentCommandId()),
                        "updatedAt", delivery.updatedAt().toString())));

        return new PlatformHealthDetectionResponse(
                jobs.size(),
                commands.size(),
                photos.size(),
                deliveries.size(),
                now);
    }

    private Object nullable(Object value) {
        return value == null ? "null" : value;
    }
}
