package com.archdox.cloud.document.application;

import com.archdox.cloud.document.domain.DocumentArtifact;
import com.archdox.cloud.document.domain.DocumentArtifactStorageKind;
import com.archdox.cloud.document.domain.DocumentDeliveryChannel;
import com.archdox.cloud.document.domain.DocumentDeliveryRequest;
import com.archdox.cloud.document.domain.DocumentDeliveryStatus;
import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.document.dto.CreateDocumentDeliveryRequest;
import com.archdox.cloud.document.dto.DocumentDeliveryRequestResponse;
import com.archdox.cloud.document.infra.DocumentArtifactRepository;
import com.archdox.cloud.document.infra.DocumentDeliveryRequestRepository;
import com.archdox.cloud.document.infra.DocumentJobRepository;
import com.archdox.cloud.document.infra.DocumentLocalObjectStore;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.ConflictException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentDeliveryService {
    private final DocumentJobRepository documentJobRepository;
    private final DocumentArtifactRepository documentArtifactRepository;
    private final DocumentDeliveryRequestRepository deliveryRequestRepository;
    private final DocumentLocalObjectStore objectStore;
    private final DocumentStorageProperties storageProperties;
    private final OperationEventService operationEventService;

    public DocumentDeliveryService(
            DocumentJobRepository documentJobRepository,
            DocumentArtifactRepository documentArtifactRepository,
            DocumentDeliveryRequestRepository deliveryRequestRepository,
            DocumentLocalObjectStore objectStore,
            DocumentStorageProperties storageProperties,
            OperationEventService operationEventService
    ) {
        this.documentJobRepository = documentJobRepository;
        this.documentArtifactRepository = documentArtifactRepository;
        this.deliveryRequestRepository = deliveryRequestRepository;
        this.objectStore = objectStore;
        this.storageProperties = storageProperties;
        this.operationEventService = operationEventService;
    }

    @Transactional
    public DocumentDeliveryRequestResponse create(
            Long documentJobId,
            CreateDocumentDeliveryRequest request,
            UserPrincipal principal
    ) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var job = documentJobRepository.findByIdAndOfficeId(documentJobId, officeId)
                .orElseThrow(() -> new NotFoundException("Document job not found"));
        if (job.status() != DocumentJobStatus.GENERATED) {
            throw new ConflictException("Document job must be GENERATED before delivery can be requested");
        }
        var artifact = resolveArtifact(officeId, documentJobId, request == null ? null : request.artifactId());
        var channel = request == null ? DocumentDeliveryChannel.DOWNLOAD : request.normalizedChannel();
        var now = OffsetDateTime.now();
        var delivery = deliveryRequestRepository.save(new DocumentDeliveryRequest(
                officeId,
                documentJobId,
                artifact.id(),
                channel,
                request == null ? null : request.recipientRef(),
                principal.userId(),
                now));
        if (channel == DocumentDeliveryChannel.DOWNLOAD && artifact.storageKind() == DocumentArtifactStorageKind.API_LOCAL) {
            delivery.markCompleted(now);
            recordDeliveryEvent(delivery, OperationEventSeverity.INFO, "DOCUMENT_DELIVERY_COMPLETED", "Document delivery is ready for direct API download.");
        }
        if (channel == DocumentDeliveryChannel.DOWNLOAD && artifact.storageKind() == DocumentArtifactStorageKind.ARCHDOX_AGENT) {
            delivery.markSending(now);
            recordDeliveryEvent(delivery, OperationEventSeverity.INFO, "DOCUMENT_DELIVERY_REQUESTED", "Document delivery workflow requested.");
        }
        if (channel == DocumentDeliveryChannel.DOWNLOAD
                && artifact.storageKind() != DocumentArtifactStorageKind.API_LOCAL
                && artifact.storageKind() != DocumentArtifactStorageKind.ARCHDOX_AGENT) {
            delivery.markFailed("Document delivery is not implemented for storage kind " + artifact.storageKind(), now);
            recordDeliveryEvent(delivery, OperationEventSeverity.ERROR, "DOCUMENT_DELIVERY_FAILED", delivery.errorMessage());
        }
        return toResponse(delivery, artifact);
    }

    @Transactional(readOnly = true)
    public List<DocumentDeliveryRequestResponse> listByJob(Long documentJobId) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        documentJobRepository.findByIdAndOfficeId(documentJobId, officeId)
                .orElseThrow(() -> new NotFoundException("Document job not found"));
        return deliveryRequestRepository.findByOfficeIdAndDocumentJobIdOrderByRequestedAtDesc(officeId, documentJobId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentDeliveryRequestResponse get(Long deliveryRequestId) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var delivery = deliveryRequestRepository.findByIdAndOfficeId(deliveryRequestId, officeId)
                .orElseThrow(() -> new NotFoundException("Document delivery request not found"));
        return toResponse(delivery);
    }

    @Transactional(readOnly = true)
    public DocumentArtifactDownload prepareDownload(Long artifactId) throws IOException {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var artifact = documentArtifactRepository.findByIdAndOfficeId(artifactId, officeId)
                .orElseThrow(() -> new NotFoundException("Document artifact not found"));
        if (artifact.storageKind() != DocumentArtifactStorageKind.API_LOCAL) {
            throw new ConflictException("Direct download is available only for API_LOCAL document artifacts");
        }
        if (!objectStore.exists(artifact.storageRef())) {
            throw new NotFoundException("Document artifact content not found");
        }
        return new DocumentArtifactDownload(
                artifact.fileName(),
                artifact.mimeType(),
                objectStore.size(artifact.storageRef()),
                outputStream -> objectStore.copyTo(artifact.storageRef(), outputStream));
    }

    @Transactional(readOnly = true)
    public DocumentArtifactDownload prepareDeliveryDownload(Long deliveryRequestId) throws IOException {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var delivery = deliveryRequestRepository.findByIdAndOfficeId(deliveryRequestId, officeId)
                .orElseThrow(() -> new NotFoundException("Document delivery request not found"));
        if (delivery.status() != DocumentDeliveryStatus.COMPLETED
                || !DocumentArtifactStorageKind.API_LOCAL.name().equals(delivery.preparedStorageKind())
                || delivery.preparedStorageRef() == null
                || delivery.preparedStorageRef().isBlank()) {
            throw new ConflictException("Document delivery is not ready for download");
        }
        var artifact = documentArtifactRepository.findByIdAndOfficeId(delivery.artifactId(), officeId)
                .orElseThrow(() -> new NotFoundException("Document artifact not found"));
        if (!objectStore.exists(delivery.preparedStorageRef())) {
            throw new NotFoundException("Prepared document delivery content not found");
        }
        return new DocumentArtifactDownload(
                artifact.fileName(),
                artifact.mimeType(),
                objectStore.size(delivery.preparedStorageRef()),
                outputStream -> objectStore.copyTo(delivery.preparedStorageRef(), outputStream));
    }

    @Transactional(readOnly = true)
    public boolean requiresAgentUpload(Long deliveryRequestId) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var delivery = deliveryRequestRepository.findByIdAndOfficeId(deliveryRequestId, officeId)
                .orElseThrow(() -> new NotFoundException("Document delivery request not found"));
        if (delivery.status() != DocumentDeliveryStatus.SENDING || delivery.artifactId() == null) {
            return false;
        }
        return documentArtifactRepository.findByIdAndOfficeId(delivery.artifactId(), officeId)
                .map(artifact -> artifact.storageKind() == DocumentArtifactStorageKind.ARCHDOX_AGENT)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public DocumentArtifact requireAgentDeliveryArtifact(Long officeId, Long deliveryRequestId) {
        var delivery = deliveryRequestRepository.findByIdAndOfficeId(deliveryRequestId, officeId)
                .orElseThrow(() -> new NotFoundException("Document delivery request not found"));
        if (delivery.status() != DocumentDeliveryStatus.SENDING) {
            throw new ConflictException("Document delivery request is not waiting for agent upload");
        }
        var artifact = documentArtifactRepository.findByIdAndOfficeId(delivery.artifactId(), officeId)
                .orElseThrow(() -> new NotFoundException("Document artifact not found"));
        if (artifact.storageKind() != DocumentArtifactStorageKind.ARCHDOX_AGENT) {
            throw new BadRequestException("Document delivery artifact is not agent-managed");
        }
        return artifact;
    }

    @Transactional
    public void markAgentDeliveryCommand(Long officeId, Long deliveryRequestId, Long commandId) {
        var delivery = deliveryRequestRepository.findByIdAndOfficeId(deliveryRequestId, officeId)
                .orElseThrow(() -> new NotFoundException("Document delivery request not found"));
        if (delivery.status() == DocumentDeliveryStatus.SENDING) {
            delivery.markSending(commandId, OffsetDateTime.now());
        }
    }

    @Transactional(readOnly = true)
    public boolean isDeliveryCompleted(Long officeId, Long deliveryRequestId) {
        return deliveryRequestRepository.findByIdAndOfficeId(deliveryRequestId, officeId)
                .map(delivery -> delivery.status() == DocumentDeliveryStatus.COMPLETED)
                .orElse(false);
    }

    @Transactional
    public void markAgentDeliveryFailed(Long officeId, Long deliveryRequestId, String message) {
        var delivery = deliveryRequestRepository.findByIdAndOfficeId(deliveryRequestId, officeId)
                .orElseThrow(() -> new NotFoundException("Document delivery request not found"));
        delivery.markFailed(message, OffsetDateTime.now());
        recordDeliveryEvent(delivery, OperationEventSeverity.ERROR, "DOCUMENT_DELIVERY_FAILED", message);
    }

    @Transactional
    public AgentDocumentDeliveryUpload receiveAgentDeliveryUpload(
            Long deliveryRequestId,
            Long officeId,
            InputStream content
    ) throws IOException {
        var delivery = deliveryRequestRepository.findByIdAndOfficeId(deliveryRequestId, officeId)
                .orElseThrow(() -> new NotFoundException("Document delivery request not found"));
        if (delivery.status() != DocumentDeliveryStatus.SENDING && delivery.status() != DocumentDeliveryStatus.COMPLETED) {
            throw new ConflictException("Document delivery request is not waiting for agent upload");
        }
        var artifact = documentArtifactRepository.findByIdAndOfficeId(delivery.artifactId(), officeId)
                .orElseThrow(() -> new NotFoundException("Document artifact not found"));
        if (artifact.storageKind() != DocumentArtifactStorageKind.ARCHDOX_AGENT) {
            throw new BadRequestException("Agent delivery upload is allowed only for ARCHDOX_AGENT artifacts");
        }

        var digest = sha256();
        var preparedStorageRef = preparedStorageRef(delivery, artifact);
        try (var input = new DigestInputStream(content, digest)) {
            objectStore.write(preparedStorageRef, input);
        }
        var actualHash = HexFormat.of().formatHex(digest.digest());
        var bytes = objectStore.size(preparedStorageRef);
        try {
            validateHashIfPresent(artifact.hashSha256(), actualHash);
            if (artifact.bytes() > 0 && artifact.bytes() != bytes) {
                throw new BadRequestException("Uploaded artifact bytes do not match metadata");
            }
        } catch (RuntimeException ex) {
            objectStore.deleteIfExists(preparedStorageRef);
            throw ex;
        }

        var now = OffsetDateTime.now();
        delivery.markPrepared(
                DocumentArtifactStorageKind.API_LOCAL.name(),
                preparedStorageRef,
                now.plusMinutes(storageProperties.safeDeliveryPreparedTtlMinutes()),
                now);
        recordDeliveryEvent(delivery, OperationEventSeverity.INFO, "DOCUMENT_DELIVERY_COMPLETED", "ArchDox Agent uploaded prepared document delivery artifact.");
        return new AgentDocumentDeliveryUpload(
                delivery.id(),
                artifact.id(),
                delivery.preparedStorageKind(),
                delivery.preparedStorageRef(),
                bytes,
                actualHash);
    }

    private DocumentArtifact resolveArtifact(Long officeId, Long documentJobId, Long requestedArtifactId) {
        if (requestedArtifactId != null) {
            var artifact = documentArtifactRepository.findByIdAndOfficeId(requestedArtifactId, officeId)
                    .orElseThrow(() -> new NotFoundException("Document artifact not found"));
            if (!artifact.documentJobId().equals(documentJobId)) {
                throw new BadRequestException("Document artifact does not belong to the requested job");
            }
            return artifact;
        }
        return documentArtifactRepository.findByDocumentJobIdOrderById(documentJobId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Document artifact not found"));
    }

    private DocumentDeliveryRequestResponse toResponse(DocumentDeliveryRequest delivery) {
        var artifact = delivery.artifactId() == null
                ? null
                : documentArtifactRepository.findByIdAndOfficeId(delivery.artifactId(), delivery.officeId()).orElse(null);
        return toResponse(delivery, artifact);
    }

    private DocumentDeliveryRequestResponse toResponse(DocumentDeliveryRequest delivery, DocumentArtifact artifact) {
        return new DocumentDeliveryRequestResponse(
                delivery.id(),
                delivery.officeId(),
                delivery.documentJobId(),
                delivery.artifactId(),
                delivery.channel(),
                delivery.status(),
                delivery.recipientRef(),
                delivery.errorMessage(),
                downloadUrl(delivery, artifact),
                delivery.requestedAt(),
                delivery.completedAt(),
                delivery.updatedAt());
    }

    private String downloadUrl(DocumentDeliveryRequest delivery, DocumentArtifact artifact) {
        if (delivery.channel() != DocumentDeliveryChannel.DOWNLOAD || artifact == null) {
            return null;
        }
        if (artifact.storageKind() != DocumentArtifactStorageKind.API_LOCAL) {
            if (delivery.status() == DocumentDeliveryStatus.COMPLETED
                    && DocumentArtifactStorageKind.API_LOCAL.name().equals(delivery.preparedStorageKind())) {
                return "/api/v1/document-delivery-requests/" + delivery.id() + "/download";
            }
            return null;
        }
        return "/api/v1/document-artifacts/" + artifact.id() + "/download";
    }

    private String preparedStorageRef(DocumentDeliveryRequest delivery, DocumentArtifact artifact) {
        return "deliveries/%d/%s".formatted(delivery.id(), safeFileName(artifact.fileName()));
    }

    private String safeFileName(String fileName) {
        var fallback = "artifact-" + System.currentTimeMillis() + ".bin";
        var value = fileName == null || fileName.isBlank() ? fallback : fileName.trim();
        return value.replace('\\', '/').replaceAll(".*/", "").replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void recordDeliveryEvent(
            DocumentDeliveryRequest delivery,
            OperationEventSeverity severity,
            String eventType,
            String message
    ) {
        operationEventService.record(
                delivery.officeId(),
                severity,
                eventType,
                "document-delivery",
                "document-delivery:" + delivery.id(),
                "DOCUMENT_DELIVERY_REQUEST",
                delivery.id(),
                message == null || message.isBlank() ? eventType : message,
                Map.of(
                        "documentJobId", delivery.documentJobId(),
                        "artifactId", delivery.artifactId(),
                        "status", delivery.status().name(),
                        "channel", delivery.channel().name()));
    }

    private void validateHashIfPresent(String expectedHash, String actualHash) {
        var normalized = normalizeHash(expectedHash);
        if (normalized != null && !normalized.equalsIgnoreCase(actualHash)) {
            throw new BadRequestException("Uploaded artifact hash does not match metadata");
        }
    }

    private String normalizeHash(String expectedHash) {
        if (expectedHash == null || expectedHash.isBlank()) {
            return null;
        }
        var text = expectedHash.trim().toLowerCase(java.util.Locale.ROOT);
        if (text.startsWith("sha256:")) {
            text = text.substring("sha256:".length());
        }
        return text.matches("[0-9a-f]{64}") ? text : null;
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }
}
