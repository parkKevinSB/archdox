package com.archdox.cloud.officeops.application;

import com.archdox.cloud.agent.domain.ArchDoxAgent;
import com.archdox.cloud.agent.domain.ArchDoxAgentCommand;
import com.archdox.cloud.agent.domain.ArchDoxAgentCommandStatus;
import com.archdox.cloud.agent.domain.ArchDoxAgentSession;
import com.archdox.cloud.agent.domain.ArchDoxAgentSessionStatus;
import com.archdox.cloud.agent.domain.ArchDoxAgentStatus;
import com.archdox.cloud.agent.infra.ArchDoxAgentCommandRepository;
import com.archdox.cloud.agent.infra.ArchDoxAgentRepository;
import com.archdox.cloud.agent.infra.ArchDoxAgentSessionRepository;
import com.archdox.cloud.document.domain.DocumentArtifact;
import com.archdox.cloud.document.domain.DocumentDeliveryRequest;
import com.archdox.cloud.document.domain.DocumentDeliveryStatus;
import com.archdox.cloud.document.domain.DocumentJob;
import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.document.infra.DocumentArtifactRepository;
import com.archdox.cloud.document.infra.DocumentDeliveryRequestRepository;
import com.archdox.cloud.document.infra.DocumentJobRepository;
import com.archdox.cloud.global.api.ForbiddenException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.infra.OfficeMembershipRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.officeops.dto.AgentCommandOpsResponse;
import com.archdox.cloud.officeops.dto.AgentOpsResponse;
import com.archdox.cloud.officeops.dto.AgentSessionOpsResponse;
import com.archdox.cloud.officeops.dto.DocumentArtifactOpsResponse;
import com.archdox.cloud.officeops.dto.DocumentDeliveryOpsResponse;
import com.archdox.cloud.officeops.dto.DocumentJobOpsResponse;
import com.archdox.cloud.officeops.dto.OfficeOpsSummaryResponse;
import com.archdox.cloud.officeops.dto.OpsCountGroupResponse;
import com.archdox.cloud.officeops.dto.PhotoAssetOpsResponse;
import com.archdox.cloud.officeops.dto.PhotoOpsResponse;
import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoAsset;
import com.archdox.cloud.photo.domain.PhotoPickupStatus;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.shared.MembershipRole;
import com.archdox.shared.MembershipStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfficeOpsReadService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final List<ArchDoxAgentCommandStatus> IN_FLIGHT_COMMAND_STATUSES = List.of(
            ArchDoxAgentCommandStatus.PENDING,
            ArchDoxAgentCommandStatus.DELIVERED,
            ArchDoxAgentCommandStatus.ACKED);
    private static final List<ArchDoxAgentCommandStatus> FAILED_COMMAND_STATUSES = List.of(
            ArchDoxAgentCommandStatus.FAILED,
            ArchDoxAgentCommandStatus.EXPIRED);

    private final OfficeMembershipRepository membershipRepository;
    private final PlatformAdminService platformAdminService;
    private final ArchDoxAgentRepository agentRepository;
    private final ArchDoxAgentSessionRepository sessionRepository;
    private final ArchDoxAgentCommandRepository commandRepository;
    private final DocumentJobRepository documentJobRepository;
    private final DocumentArtifactRepository artifactRepository;
    private final PhotoRepository photoRepository;
    private final PhotoAssetRepository photoAssetRepository;
    private final DocumentDeliveryRequestRepository deliveryRepository;

    public OfficeOpsReadService(
            OfficeMembershipRepository membershipRepository,
            PlatformAdminService platformAdminService,
            ArchDoxAgentRepository agentRepository,
            ArchDoxAgentSessionRepository sessionRepository,
            ArchDoxAgentCommandRepository commandRepository,
            DocumentJobRepository documentJobRepository,
            DocumentArtifactRepository artifactRepository,
            PhotoRepository photoRepository,
            PhotoAssetRepository photoAssetRepository,
            DocumentDeliveryRequestRepository deliveryRepository
    ) {
        this.membershipRepository = membershipRepository;
        this.platformAdminService = platformAdminService;
        this.agentRepository = agentRepository;
        this.sessionRepository = sessionRepository;
        this.commandRepository = commandRepository;
        this.documentJobRepository = documentJobRepository;
        this.artifactRepository = artifactRepository;
        this.photoRepository = photoRepository;
        this.photoAssetRepository = photoAssetRepository;
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional(readOnly = true)
    public OfficeOpsSummaryResponse getSummary(UserPrincipal principal) {
        var officeId = requireOfficeAdmin(principal);
        return new OfficeOpsSummaryResponse(
                officeId,
                countGroup(
                        agentRepository.countByOfficeId(officeId),
                        ArchDoxAgentStatus.values(),
                        status -> agentRepository.countByOfficeIdAndStatus(officeId, status)),
                sessionRepository.countByOfficeIdAndStatus(officeId, ArchDoxAgentSessionStatus.ACTIVE),
                commandRepository.countByOfficeIdAndStatusIn(officeId, IN_FLIGHT_COMMAND_STATUSES),
                countGroup(
                        documentJobRepository.countByOfficeId(officeId),
                        DocumentJobStatus.values(),
                        status -> documentJobRepository.countByOfficeIdAndStatus(officeId, status)),
                countGroup(
                        photoRepository.countByOfficeId(officeId),
                        PhotoStatus.values(),
                        status -> photoRepository.countByOfficeIdAndStatus(officeId, status)),
                countGroup(
                        photoRepository.countByOfficeId(officeId),
                        PhotoPickupStatus.values(),
                        status -> photoRepository.countByOfficeIdAndOriginalPickupStatus(officeId, status)),
                countGroup(
                        deliveryRepository.countByOfficeId(officeId),
                        DocumentDeliveryStatus.values(),
                        status -> deliveryRepository.countByOfficeIdAndStatus(officeId, status)),
                OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<AgentOpsResponse> listAgents(UserPrincipal principal, Integer limit) {
        var officeId = requireOfficeAdmin(principal);
        return agentRepository.findByOfficeIdOrderByLastSeenAtDesc(officeId, PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(this::toAgentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AgentSessionOpsResponse> listAgentSessions(UserPrincipal principal, Integer limit) {
        var officeId = requireOfficeAdmin(principal);
        return sessionRepository.findByOfficeIdOrderByLastSeenAtDesc(officeId, PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AgentCommandOpsResponse> listAgentCommands(
            UserPrincipal principal,
            Long agentId,
            ArchDoxAgentCommandStatus status,
            Integer limit
    ) {
        var officeId = requireOfficeAdmin(principal);
        return commandRepository.searchOfficeCommands(
                        officeId,
                        agentId,
                        status,
                        PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(this::toCommandResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentJobOpsResponse> listDocumentJobs(
            UserPrincipal principal,
            DocumentJobStatus status,
            Integer limit
    ) {
        var officeId = requireOfficeAdmin(principal);
        var jobs = documentJobRepository.searchOfficeJobs(officeId, status, PageRequest.of(0, normalizeLimit(limit)));
        var artifactsByJob = artifactRepository.findByDocumentJobIdInOrderByDocumentJobIdAscIdAsc(ids(jobs, DocumentJob::id))
                .stream()
                .collect(Collectors.groupingBy(DocumentArtifact::documentJobId));
        return jobs.stream()
                .map(job -> toDocumentJobResponse(job, artifactsByJob.getOrDefault(job.id(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PhotoOpsResponse> listPhotos(
            UserPrincipal principal,
            PhotoStatus status,
            PhotoPickupStatus originalPickupStatus,
            Integer limit
    ) {
        var officeId = requireOfficeAdmin(principal);
        var photos = photoRepository.searchOfficePhotos(
                officeId,
                status,
                originalPickupStatus,
                PageRequest.of(0, normalizeLimit(limit)));
        var assetsByPhoto = photoAssetRepository.findByPhotoIdInOrderByPhotoIdAscIdAsc(ids(photos, Photo::id))
                .stream()
                .collect(Collectors.groupingBy(asset -> asset.photo().id()));
        return photos.stream()
                .map(photo -> toPhotoResponse(photo, assetsByPhoto.getOrDefault(photo.id(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentDeliveryOpsResponse> listDeliveries(
            UserPrincipal principal,
            DocumentDeliveryStatus status,
            Integer limit
    ) {
        var officeId = requireOfficeAdmin(principal);
        return deliveryRepository.searchOfficeDeliveries(officeId, status, PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(this::toDeliveryResponse)
                .toList();
    }

    private Long requireOfficeAdmin(UserPrincipal principal) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        if (platformAdminService.isPlatformAdmin(principal)) {
            return officeId;
        }
        var membership = membershipRepository.findByUserIdAndOfficeIdAndStatus(
                        principal.userId(),
                        officeId,
                        MembershipStatus.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("Office membership required"));
        if (membership.role() != MembershipRole.OWNER && membership.role() != MembershipRole.ADMIN) {
            throw new ForbiddenException("Office admin role required");
        }
        return officeId;
    }

    private AgentOpsResponse toAgentResponse(ArchDoxAgent agent) {
        var activeSessions = sessionRepository.findByAgentIdAndStatus(agent.id(), ArchDoxAgentSessionStatus.ACTIVE)
                .stream()
                .map(this::toSessionResponse)
                .toList();
        return new AgentOpsResponse(
                agent.id(),
                agent.officeId(),
                agent.agentCode(),
                agent.deploymentMode(),
                agent.status(),
                agent.authMode(),
                agent.version(),
                agent.lastSeenAt(),
                agent.lastAuthenticatedAt(),
                agent.pairedAt(),
                agent.registeredAt(),
                activeSessions.size(),
                commandRepository.countByAgentIdAndStatusIn(agent.id(), IN_FLIGHT_COMMAND_STATUSES),
                commandRepository.countByAgentIdAndStatusIn(agent.id(), FAILED_COMMAND_STATUSES),
                safeMap(agent.capabilitiesJson()),
                safeMap(agent.storageProfileJson()),
                activeSessions);
    }

    private AgentSessionOpsResponse toSessionResponse(ArchDoxAgentSession session) {
        return new AgentSessionOpsResponse(
                session.id(),
                session.agent().id(),
                session.apiInstanceId(),
                session.websocketSessionId(),
                session.status(),
                session.connectedAt(),
                session.lastSeenAt(),
                session.disconnectedAt(),
                session.disconnectReason());
    }

    private AgentCommandOpsResponse toCommandResponse(ArchDoxAgentCommand command) {
        return new AgentCommandOpsResponse(
                command.id(),
                command.agent().id(),
                command.agent().agentCode(),
                command.commandType(),
                command.status(),
                command.attemptCount(),
                command.maxAttempts(),
                command.createdAt(),
                command.deliveredAt(),
                command.ackAt(),
                command.completedAt(),
                command.failedAt(),
                command.lastAttemptAt(),
                command.nextAttemptAt(),
                command.expiresAt(),
                command.errorMessage());
    }

    private DocumentJobOpsResponse toDocumentJobResponse(DocumentJob job, List<DocumentArtifact> artifacts) {
        return new DocumentJobOpsResponse(
                job.id(),
                job.officeId(),
                job.reportId(),
                job.projectId(),
                job.reportRevision(),
                job.templateId(),
                job.status(),
                job.progressStep(),
                job.progressPercent(),
                job.progressMessage(),
                job.requestedBy(),
                job.workerType(),
                job.outputFormat(),
                job.errorCode(),
                job.errorMessage(),
                job.requestedAt(),
                job.startedAt(),
                job.completedAt(),
                job.updatedAt(),
                artifacts.stream().map(this::toArtifactResponse).toList());
    }

    private DocumentArtifactOpsResponse toArtifactResponse(DocumentArtifact artifact) {
        return new DocumentArtifactOpsResponse(
                artifact.id(),
                artifact.documentJobId(),
                artifact.reportId(),
                artifact.artifactType(),
                artifact.storageKind(),
                artifact.fileName(),
                artifact.mimeType(),
                artifact.bytes(),
                artifact.hashSha256(),
                artifact.createdAt());
    }

    private PhotoOpsResponse toPhotoResponse(Photo photo, List<PhotoAsset> assets) {
        return new PhotoOpsResponse(
                photo.id(),
                photo.officeId(),
                photo.projectId(),
                photo.siteId(),
                photo.reportId(),
                photo.stepCode(),
                photo.checklistItemId(),
                photo.siteSupervisionEntryId(),
                photo.tradeCode(),
                photo.processCode(),
                photo.inspectionItemCode(),
                photo.caption(),
                photo.locationNote(),
                photo.drawingRef(),
                photo.captureKind(),
                photo.status(),
                photo.mimeType(),
                photo.width(),
                photo.height(),
                photo.bytes(),
                photo.hashSha256(),
                photo.storageKind(),
                photo.uploadTarget(),
                photo.originalPickupStatus(),
                photo.requestedBy(),
                photo.confirmedBy(),
                photo.takenAt(),
                photo.hasGps(),
                photo.createdAt(),
                photo.confirmedAt(),
                photo.originalPickedUpAt(),
                photo.originalTemporaryDeletedAt(),
                photo.pickupErrorMessage(),
                photo.updatedAt(),
                assets.stream().map(this::toPhotoAssetResponse).toList());
    }

    private PhotoAssetOpsResponse toPhotoAssetResponse(PhotoAsset asset) {
        return new PhotoAssetOpsResponse(
                asset.id(),
                asset.photo().id(),
                asset.assetType(),
                asset.status(),
                asset.storageKind(),
                asset.mimeType(),
                asset.bytes(),
                asset.width(),
                asset.height(),
                asset.hashSha256(),
                asset.temporary(),
                asset.createdAt(),
                asset.uploadedAt(),
                asset.pickedUpAt(),
                asset.deletedAt());
    }

    private DocumentDeliveryOpsResponse toDeliveryResponse(DocumentDeliveryRequest delivery) {
        return new DocumentDeliveryOpsResponse(
                delivery.id(),
                delivery.officeId(),
                delivery.documentJobId(),
                delivery.artifactId(),
                delivery.channel(),
                delivery.status(),
                delivery.recipientRef(),
                delivery.requestedBy(),
                delivery.errorMessage(),
                delivery.preparedStorageKind(),
                delivery.preparedExpiresAt(),
                delivery.downloadReadyAt(),
                delivery.agentCommandId(),
                delivery.requestedAt(),
                delivery.completedAt(),
                delivery.updatedAt());
    }

    private <E extends Enum<E>> OpsCountGroupResponse countGroup(
            long total,
            E[] values,
            Function<E, Long> counter
    ) {
        var counts = new EnumMap<E, Long>(values[0].getDeclaringClass());
        for (E value : values) {
            counts.put(value, counter.apply(value));
        }
        return new OpsCountGroupResponse(total, stringKeyMap(counts));
    }

    private Map<String, Long> stringKeyMap(Map<? extends Enum<?>, Long> counts) {
        return counts.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().name(),
                        Map.Entry::getValue,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));
    }

    private int normalizeLimit(Integer limit) {
        return Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : Map.copyOf(value);
    }

    private <T> List<Long> ids(Collection<T> values, Function<T, Long> idReader) {
        if (values.isEmpty()) {
            return new ArrayList<>();
        }
        return values.stream().map(idReader).toList();
    }
}
