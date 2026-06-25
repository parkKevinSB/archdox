package com.archdox.cloud.platformadmin.application;

import com.archdox.cloud.account.domain.UserAccount;
import com.archdox.cloud.account.infra.UserAccountRepository;
import com.archdox.cloud.agent.domain.ArchDoxAgent;
import com.archdox.cloud.agent.domain.ArchDoxAgentCommand;
import com.archdox.cloud.agent.domain.ArchDoxAgentCommandStatus;
import com.archdox.cloud.agent.domain.ArchDoxAgentSessionStatus;
import com.archdox.cloud.agent.domain.ArchDoxAgentStatus;
import com.archdox.cloud.agent.infra.ArchDoxAgentCommandRepository;
import com.archdox.cloud.agent.infra.ArchDoxAgentRepository;
import com.archdox.cloud.agent.infra.ArchDoxAgentSessionRepository;
import com.archdox.cloud.document.domain.DocumentDeliveryRequest;
import com.archdox.cloud.document.domain.DocumentDeliveryStatus;
import com.archdox.cloud.document.domain.DocumentJob;
import com.archdox.cloud.document.domain.DocumentJobStatus;
import com.archdox.cloud.document.infra.DocumentDeliveryRequestRepository;
import com.archdox.cloud.document.infra.DocumentJobRepository;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.monitoring.application.ServerRuntimeHealthService;
import com.archdox.cloud.monitoring.dto.PlatformServerRuntimeHealthResponse;
import com.archdox.cloud.monitoring.dto.ServerRuntimeHealthSettingsResponse;
import com.archdox.cloud.monitoring.dto.UpdateServerRuntimeHealthSettingsRequest;
import com.archdox.cloud.office.domain.Office;
import com.archdox.cloud.office.infra.OfficeRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.dto.OperationEventResponse;
import com.archdox.cloud.operation.infra.OperationEventRepository;
import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoPickupStatus;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.cloud.platformadmin.dto.PlatformAgentCommandOpsResponse;
import com.archdox.cloud.platformadmin.dto.PlatformAgentOpsResponse;
import com.archdox.cloud.platformadmin.dto.PlatformDeliveryOpsResponse;
import com.archdox.cloud.platformadmin.dto.PlatformDocumentJobOpsResponse;
import com.archdox.cloud.platformadmin.dto.PlatformOfficeOpsResponse;
import com.archdox.cloud.platformadmin.dto.PlatformOpsSummaryResponse;
import com.archdox.cloud.platformadmin.dto.PlatformPhotoOpsResponse;
import com.archdox.cloud.platformadmin.dto.PlatformUserOpsResponse;
import com.archdox.cloud.platformops.application.PlatformOpsAutomationSettingsService;
import com.archdox.cloud.platformops.dto.PlatformOpsAutomationSettingsResponse;
import com.archdox.cloud.platformops.dto.UpdatePlatformOpsAutomationSettingsRequest;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformOpsReadService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final List<ArchDoxAgentCommandStatus> IN_FLIGHT_COMMAND_STATUSES = List.of(
            ArchDoxAgentCommandStatus.PENDING,
            ArchDoxAgentCommandStatus.DELIVERED,
            ArchDoxAgentCommandStatus.ACKED);

    private final PlatformAdminService platformAdminService;
    private final UserAccountRepository userRepository;
    private final OfficeRepository officeRepository;
    private final ArchDoxAgentRepository agentRepository;
    private final ArchDoxAgentSessionRepository sessionRepository;
    private final ArchDoxAgentCommandRepository commandRepository;
    private final DocumentJobRepository documentJobRepository;
    private final PhotoRepository photoRepository;
    private final DocumentDeliveryRequestRepository deliveryRepository;
    private final OperationEventRepository eventRepository;
    private final OperationEventService operationEventService;
    private final ServerRuntimeHealthService serverRuntimeHealthService;
    private final PlatformOpsAutomationSettingsService automationSettingsService;

    public PlatformOpsReadService(
            PlatformAdminService platformAdminService,
            UserAccountRepository userRepository,
            OfficeRepository officeRepository,
            ArchDoxAgentRepository agentRepository,
            ArchDoxAgentSessionRepository sessionRepository,
            ArchDoxAgentCommandRepository commandRepository,
            DocumentJobRepository documentJobRepository,
            PhotoRepository photoRepository,
            DocumentDeliveryRequestRepository deliveryRepository,
            OperationEventRepository eventRepository,
            OperationEventService operationEventService,
            ServerRuntimeHealthService serverRuntimeHealthService,
            PlatformOpsAutomationSettingsService automationSettingsService
    ) {
        this.platformAdminService = platformAdminService;
        this.userRepository = userRepository;
        this.officeRepository = officeRepository;
        this.agentRepository = agentRepository;
        this.sessionRepository = sessionRepository;
        this.commandRepository = commandRepository;
        this.documentJobRepository = documentJobRepository;
        this.photoRepository = photoRepository;
        this.deliveryRepository = deliveryRepository;
        this.eventRepository = eventRepository;
        this.operationEventService = operationEventService;
        this.serverRuntimeHealthService = serverRuntimeHealthService;
        this.automationSettingsService = automationSettingsService;
    }

    @Transactional(readOnly = true)
    public PlatformOpsSummaryResponse summary(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        return new PlatformOpsSummaryResponse(
                userRepository.count(),
                officeRepository.count(),
                countGroup(ArchDoxAgentStatus.values(), agentRepository::countByStatus),
                sessionRepository.countByStatus(ArchDoxAgentSessionStatus.ACTIVE),
                commandSummary(),
                countGroup(DocumentJobStatus.values(), documentJobRepository::countByStatus),
                countGroup(PhotoStatus.values(), photoRepository::countByStatus),
                countGroup(PhotoPickupStatus.values(), photoRepository::countByOriginalPickupStatus),
                countGroup(DocumentDeliveryStatus.values(), deliveryRepository::countByStatus),
                serverRuntimeHealthService.latestOrSample(),
                OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public PlatformServerRuntimeHealthResponse serverRuntime(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        return new PlatformServerRuntimeHealthResponse(
                serverRuntimeHealthService.latestOrSample(),
                serverRuntimeHealthService.settings());
    }

    @Transactional
    public ServerRuntimeHealthSettingsResponse updateServerRuntimeSettings(
            UserPrincipal principal,
            UpdateServerRuntimeHealthSettingsRequest request
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        return serverRuntimeHealthService.updateSettings(request, principal.userId());
    }

    @Transactional(readOnly = true)
    public PlatformOpsAutomationSettingsResponse automationSettings(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        return automationSettingsService.settings();
    }

    @Transactional
    public PlatformOpsAutomationSettingsResponse updateAutomationSettings(
            UserPrincipal principal,
            UpdatePlatformOpsAutomationSettingsRequest request
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        return automationSettingsService.updateSettings(request, principal.userId());
    }

    @Transactional(readOnly = true)
    public List<PlatformUserOpsResponse> users(UserPrincipal principal, Integer limit) {
        platformAdminService.requirePlatformAdmin(principal);
        return userRepository.findAll(PageRequest.of(0, normalizeLimit(limit), Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(this::toUser)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformOfficeOpsResponse> offices(UserPrincipal principal, Integer limit) {
        platformAdminService.requirePlatformAdmin(principal);
        return officeRepository.findAll(PageRequest.of(0, normalizeLimit(limit), Sort.by(Sort.Direction.DESC, "id")))
                .stream()
                .map(this::toOffice)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformAgentOpsResponse> agents(UserPrincipal principal, Integer limit) {
        platformAdminService.requirePlatformAdmin(principal);
        return agentRepository.findAllByOrderByLastSeenAtDesc(PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(this::toAgent)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformAgentCommandOpsResponse> commands(
            UserPrincipal principal,
            Long officeId,
            Long agentId,
            ArchDoxAgentCommandStatus status,
            Integer limit
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        return commandRepository.searchPlatformCommands(officeId, agentId, status, PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(this::toCommand)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformDocumentJobOpsResponse> documentJobs(
            UserPrincipal principal,
            Long officeId,
            DocumentJobStatus status,
            Integer limit
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        return documentJobRepository.searchPlatformJobs(officeId, status, PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(this::toDocumentJob)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformPhotoOpsResponse> photos(
            UserPrincipal principal,
            Long officeId,
            PhotoStatus status,
            PhotoPickupStatus pickupStatus,
            Integer limit
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        return photoRepository.searchPlatformPhotos(officeId, status, pickupStatus, PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(this::toPhoto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformDeliveryOpsResponse> deliveries(
            UserPrincipal principal,
            Long officeId,
            DocumentDeliveryStatus status,
            Integer limit
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        return deliveryRepository.searchPlatformDeliveries(officeId, status, PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(this::toDelivery)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OperationEventResponse> events(
            UserPrincipal principal,
            Long officeId,
            String eventType,
            String workflowType,
            String workflowKey,
            String resourceType,
            String resourceId,
            Integer limit
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        return eventRepository.searchPlatformEvents(
                        officeId,
                        blankToNull(eventType),
                        blankToNull(workflowType),
                        blankToNull(workflowKey),
                        blankToNull(resourceType),
                        blankToNull(resourceId),
                        PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(operationEventService::toResponse)
                .toList();
    }

    private Map<String, Long> commandSummary() {
        var counts = new java.util.LinkedHashMap<String, Long>();
        Arrays.stream(ArchDoxAgentCommandStatus.values())
                .forEach(status -> counts.put(status.name(), commandRepository.countByStatus(status)));
        counts.put("IN_FLIGHT", commandRepository.countByStatusIn(IN_FLIGHT_COMMAND_STATUSES));
        return counts;
    }

    private <E extends Enum<E>> Map<String, Long> countGroup(E[] values, Function<E, Long> counter) {
        var counts = new EnumMap<E, Long>(values[0].getDeclaringClass());
        Arrays.stream(values).forEach(value -> counts.put(value, counter.apply(value)));
        var result = new java.util.LinkedHashMap<String, Long>();
        counts.forEach((key, value) -> result.put(key.name(), value));
        return result;
    }

    private PlatformUserOpsResponse toUser(UserAccount user) {
        return new PlatformUserOpsResponse(user.id(), user.email(), user.name(), user.status(), user.createdAt());
    }

    private PlatformOfficeOpsResponse toOffice(Office office) {
        return new PlatformOfficeOpsResponse(
                office.id(),
                office.officeCode(),
                office.displayName(),
                office.type(),
                office.planCode(),
                office.status());
    }

    private PlatformAgentOpsResponse toAgent(ArchDoxAgent agent) {
        return new PlatformAgentOpsResponse(
                agent.id(),
                agent.officeId(),
                agent.agentCode(),
                agent.deploymentMode(),
                agent.status(),
                agent.version(),
                agent.lastSeenAt(),
                safeMap(agent.capabilitiesJson()),
                compatibilityMap(agent.capabilitiesJson()),
                safeMap(agent.storageProfileJson()));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> compatibilityMap(Map<String, Object> capabilities) {
        if (capabilities == null) {
            return Map.of();
        }
        var compatibility = capabilities.get("compatibility");
        if (compatibility instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private PlatformAgentCommandOpsResponse toCommand(ArchDoxAgentCommand command) {
        return new PlatformAgentCommandOpsResponse(
                command.id(),
                command.agent().officeId(),
                command.agent().id(),
                command.agent().agentCode(),
                command.commandType(),
                command.status(),
                command.attemptCount(),
                command.maxAttempts(),
                command.createdAt(),
                command.lastAttemptAt(),
                command.nextAttemptAt(),
                command.expiresAt(),
                command.errorMessage());
    }

    private PlatformDocumentJobOpsResponse toDocumentJob(DocumentJob job) {
        return new PlatformDocumentJobOpsResponse(
                job.id(),
                job.officeId(),
                job.reportId(),
                job.projectId(),
                job.reportRevision(),
                job.status(),
                job.progressStep(),
                job.progressPercent(),
                job.workerType(),
                job.outputFormat(),
                job.errorCode(),
                job.errorMessage(),
                job.requestedAt(),
                job.updatedAt());
    }

    private PlatformPhotoOpsResponse toPhoto(Photo photo) {
        return new PlatformPhotoOpsResponse(
                photo.id(),
                photo.officeId(),
                photo.projectId(),
                photo.reportId(),
                photo.stepCode(),
                photo.status(),
                photo.originalPickupStatus(),
                photo.uploadTarget(),
                photo.storageKind(),
                photo.bytes(),
                photo.pickupErrorMessage(),
                photo.createdAt(),
                photo.updatedAt());
    }

    private PlatformDeliveryOpsResponse toDelivery(DocumentDeliveryRequest delivery) {
        return new PlatformDeliveryOpsResponse(
                delivery.id(),
                delivery.officeId(),
                delivery.documentJobId(),
                delivery.artifactId(),
                delivery.channel(),
                delivery.status(),
                delivery.agentCommandId(),
                delivery.errorMessage(),
                delivery.requestedAt(),
                delivery.updatedAt());
    }

    private int normalizeLimit(Integer limit) {
        return Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
