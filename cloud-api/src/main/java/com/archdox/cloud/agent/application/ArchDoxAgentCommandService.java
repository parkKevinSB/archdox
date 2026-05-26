package com.archdox.cloud.agent.application;

import com.archdox.cloud.agent.domain.ArchDoxAgent;
import com.archdox.cloud.agent.domain.ArchDoxAgentCommand;
import com.archdox.cloud.agent.domain.ArchDoxAgentCommandStatus;
import com.archdox.cloud.agent.domain.ArchDoxAgentCommandType;
import com.archdox.cloud.agent.domain.ArchDoxAgentDeploymentMode;
import com.archdox.cloud.agent.domain.ArchDoxAgentHeartbeat;
import com.archdox.cloud.agent.domain.ArchDoxAgentSessionStatus;
import com.archdox.cloud.agent.domain.ArchDoxAgentStatus;
import com.archdox.cloud.agent.infra.ArchDoxAgentCommandRepository;
import com.archdox.cloud.agent.infra.ArchDoxAgentHeartbeatRepository;
import com.archdox.cloud.agent.infra.ArchDoxAgentRepository;
import com.archdox.cloud.agent.infra.ArchDoxAgentSessionRepository;
import com.archdox.cloud.document.domain.DocumentArtifact;
import com.archdox.cloud.document.domain.DocumentArtifactStorageKind;
import com.archdox.cloud.document.event.DocumentDeliveryCommandAckedEvent;
import com.archdox.cloud.document.event.DocumentDeliveryCommandCompletedEvent;
import com.archdox.cloud.document.event.DocumentDeliveryCommandFailedEvent;
import com.archdox.cloud.document.event.DocumentRenderCommandAckedEvent;
import com.archdox.cloud.document.event.DocumentRenderCommandCompletedEvent;
import com.archdox.cloud.document.event.DocumentRenderCommandFailedEvent;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.api.UnauthorizedException;
import com.archdox.cloud.office.infra.OfficeRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.photo.application.PhotoPickupService;
import com.archdox.cloud.photo.event.PhotoPickupCommandAckedEvent;
import com.archdox.cloud.photo.event.PhotoPickupCommandCompletedEvent;
import com.archdox.cloud.photo.event.PhotoPickupCommandFailedEvent;
import com.archdox.document.OutputFormat;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.github.parkkevinsb.bloom.EventBus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ArchDoxAgentCommandService {
    private final ArchDoxAgentRepository agentRepository;
    private final ArchDoxAgentHeartbeatRepository heartbeatRepository;
    private final ArchDoxAgentCommandRepository commandRepository;
    private final ArchDoxAgentSessionRepository sessionRepository;
    private final OfficeRepository officeRepository;
    private final PhotoPickupService photoPickupService;
    private final ArchDoxAgentSessionRegistry sessionRegistry;
    private final ArchDoxAgentProperties properties;
    private final EventBus eventBus;
    private final OperationEventService operationEventService;
    private final TransactionTemplate commandDeliveryTransactionTemplate;

    public ArchDoxAgentCommandService(
            ArchDoxAgentRepository agentRepository,
            ArchDoxAgentHeartbeatRepository heartbeatRepository,
            ArchDoxAgentCommandRepository commandRepository,
            ArchDoxAgentSessionRepository sessionRepository,
            OfficeRepository officeRepository,
            PhotoPickupService photoPickupService,
            ArchDoxAgentSessionRegistry sessionRegistry,
            ArchDoxAgentProperties properties,
            EventBus eventBus,
            OperationEventService operationEventService,
            PlatformTransactionManager transactionManager
    ) {
        this.agentRepository = agentRepository;
        this.heartbeatRepository = heartbeatRepository;
        this.commandRepository = commandRepository;
        this.sessionRepository = sessionRepository;
        this.officeRepository = officeRepository;
        this.photoPickupService = photoPickupService;
        this.sessionRegistry = sessionRegistry;
        this.properties = properties;
        this.eventBus = eventBus;
        this.operationEventService = operationEventService;
        this.commandDeliveryTransactionTemplate = new TransactionTemplate(transactionManager);
        this.commandDeliveryTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public ArchDoxAgent connect(AgentHello hello) {
        if (hello.officeId() == null || hello.agentCode() == null || hello.agentCode().isBlank()) {
            throw new BadRequestException("officeId and agentCode are required");
        }
        if (!properties.getSharedSecret().equals(hello.token())) {
            throw new UnauthorizedException("Invalid agent token");
        }
        if (!officeRepository.existsById(hello.officeId())) {
            throw new NotFoundException("Office not found");
        }
        var now = OffsetDateTime.now();
        var agent = agentRepository.findByOfficeIdAndAgentCode(hello.officeId(), hello.agentCode().trim())
                .orElseGet(() -> agentRepository.save(new ArchDoxAgent(
                        hello.officeId(),
                        hello.agentCode().trim(),
                        deploymentMode(hello),
                        hello.version(),
                        hello.capabilities(),
                        hello.storageProfile(),
                        now)));
        agent.markOnline(deploymentMode(hello), hello.version(), hello.capabilities(), hello.storageProfile(), now);
        return agent;
    }

    @Transactional
    public void disconnect(Long agentId) {
        if (agentId == null) {
            return;
        }
        if (sessionRepository.existsByAgentIdAndStatus(agentId, ArchDoxAgentSessionStatus.ACTIVE)) {
            return;
        }
        var now = OffsetDateTime.now();
        agentRepository.findById(agentId)
                .ifPresent(agent -> agent.markOffline(now));
        failInFlightCommandsForDisconnectedAgent(agentId, now);
    }

    @Transactional
    public void heartbeat(Long agentId, AgentHeartbeat heartbeat) {
        var agent = requireAgent(agentId);
        var now = OffsetDateTime.now();
        agent.markSeen(now);
        heartbeatRepository.save(new ArchDoxAgentHeartbeat(
                agent,
                heartbeat.version(),
                heartbeat.diskFreeBytes(),
                heartbeat.pendingJobs(),
                heartbeat.recentErrorCount(),
                now));
    }

    @Transactional
    public Optional<Long> enqueuePhotoPickup(
            Long officeId,
            Long photoId,
            int attempt,
            int maxAttempts
    ) {
        var agent = selectCommandTargetAgent(officeId, false);
        if (agent.isEmpty()) {
            return Optional.empty();
        }
        var now = OffsetDateTime.now();
        var expiresAt = now.plusMinutes(properties.getCommandTtlMinutes());
        var payload = photoPickupService.buildCommandPayload(officeId, photoId, attempt, maxAttempts, expiresAt);
        var command = commandRepository.save(new ArchDoxAgentCommand(
                agent.get(),
                ArchDoxAgentCommandType.PHOTO_PICKUP,
                payload,
                now,
                expiresAt));
        command.configureRetry(1, now);
        recordCommandEvent(command, OperationEventSeverity.INFO, "AGENT_COMMAND_ENQUEUED", "PHOTO_PICKUP command enqueued.");
        deliverAfterCommit(command.id());
        return Optional.of(command.id());
    }

    @Transactional
    public Optional<Long> enqueueDocumentRender(
            Long officeId,
            Long documentJobId,
            Map<String, Object> renderPayload,
            int attempt,
            int maxAttempts
    ) {
        var outputFormat = outputFormat(renderPayload);
        var agent = selectDocumentRenderTargetAgent(officeId, outputFormat);
        if (agent.isEmpty()) {
            return Optional.empty();
        }
        var now = OffsetDateTime.now();
        var expiresAt = now.plusMinutes(properties.getCommandTtlMinutes());
        var payload = new LinkedHashMap<String, Object>(renderPayload == null ? Map.of() : renderPayload);
        payload.put("officeId", officeId);
        payload.put("documentJobId", documentJobId);
        payload.put("attempt", Math.max(1, attempt));
        payload.put("maxAttempts", Math.max(1, maxAttempts));
        payload.put("expiresAt", expiresAt);
        var command = commandRepository.save(new ArchDoxAgentCommand(
                agent.get(),
                ArchDoxAgentCommandType.GENERATE_DOCUMENT,
                payload,
                now,
                expiresAt));
        command.configureRetry(1, now);
        recordCommandEvent(command, OperationEventSeverity.INFO, "AGENT_COMMAND_ENQUEUED", "GENERATE_DOCUMENT command enqueued.");
        deliverAfterCommit(command.id());
        return Optional.of(command.id());
    }

    @Transactional(readOnly = true)
    public boolean hasDocumentRenderTarget(Long officeId, OutputFormat outputFormat) {
        return selectDocumentRenderTargetAgent(officeId, outputFormat).isPresent();
    }

    @Transactional
    public Optional<Long> enqueueDocumentArtifactDelivery(
            Long officeId,
            Long deliveryRequestId,
            DocumentArtifact artifact,
            int attempt,
            int maxAttempts
    ) {
        var agent = selectCommandTargetAgent(officeId, false);
        if (agent.isEmpty()) {
            return Optional.empty();
        }
        var now = OffsetDateTime.now();
        var expiresAt = now.plusMinutes(properties.getCommandTtlMinutes());
        var payload = new LinkedHashMap<String, Object>();
        payload.put("officeId", officeId);
        payload.put("deliveryRequestId", deliveryRequestId);
        payload.put("documentJobId", artifact.documentJobId());
        payload.put("artifactId", artifact.id());
        payload.put("sourceStorageKind", artifact.storageKind().name());
        payload.put("sourceStorageRef", artifact.storageRef());
        payload.put("fileName", artifact.fileName());
        payload.put("mimeType", artifact.mimeType());
        payload.put("bytes", artifact.bytes());
        payload.put("hashSha256", artifact.hashSha256());
        payload.put("uploadMethod", "PUT_MULTIPART");
        payload.put("uploadUrl", "/agent/api/v1/document-delivery-requests/%d/content".formatted(deliveryRequestId));
        payload.put("resultStorageKind", DocumentArtifactStorageKind.API_LOCAL.name());
        payload.put("attempt", Math.max(1, attempt));
        payload.put("maxAttempts", Math.max(1, maxAttempts));
        payload.put("expiresAt", expiresAt);
        var command = commandRepository.save(new ArchDoxAgentCommand(
                agent.get(),
                ArchDoxAgentCommandType.UPLOAD_DOCUMENT_ARTIFACT,
                payload,
                now,
                expiresAt));
        command.configureRetry(1, now);
        recordCommandEvent(command, OperationEventSeverity.INFO, "AGENT_COMMAND_ENQUEUED", "UPLOAD_DOCUMENT_ARTIFACT command enqueued.");
        deliverAfterCommit(command.id());
        return Optional.of(command.id());
    }

    @Transactional
    public void deliverPending(Long agentId) {
        var now = OffsetDateTime.now();
        commandRepository.findByAgentIdAndStatusInOrderByCreatedAtAsc(
                        agentId,
                        List.of(ArchDoxAgentCommandStatus.PENDING))
                .stream()
                .filter(command -> command.isDue(now))
                .forEach(this::deliver);
    }

    @Transactional
    public int expireInFlightCommandsForRuntimeRecovery() {
        var now = OffsetDateTime.now();
        var commands = commandRepository.findByStatusInOrderByCreatedAtAsc(List.of(
                ArchDoxAgentCommandStatus.PENDING,
                ArchDoxAgentCommandStatus.DELIVERED,
                ArchDoxAgentCommandStatus.ACKED));
        commands.forEach(command -> {
            command.expire("Cloud API restarted before command completed", now);
            recordCommandEvent(command, OperationEventSeverity.WARN, "AGENT_COMMAND_EXPIRED_FOR_RECOVERY", "Agent command expired during Cloud API flow recovery.");
        });
        return commands.size();
    }

    private void failInFlightCommandsForDisconnectedAgent(Long agentId, OffsetDateTime now) {
        var message = "ArchDox Agent disconnected before command completed";
        var errorCode = "ARCHDOX_AGENT_DISCONNECTED";
        var commands = commandRepository.findByAgentIdAndStatusInOrderByCreatedAtAsc(
                agentId,
                List.of(
                        ArchDoxAgentCommandStatus.PENDING,
                        ArchDoxAgentCommandStatus.DELIVERED,
                        ArchDoxAgentCommandStatus.ACKED));
        commands.forEach(command -> {
            if (command.isTerminal()) {
                return;
            }
            var result = failureResult(errorCode, false, message, Map.of());
            command.fail(message, result, now);
            recordCommandEvent(
                    command,
                    OperationEventSeverity.WARN,
                    "AGENT_COMMAND_FAILED_AGENT_DISCONNECTED",
                    message);
            publishAgentDisconnectedFailure(command, errorCode, message, result, now);
        });
    }

    private void publishAgentDisconnectedFailure(
            ArchDoxAgentCommand command,
            String errorCode,
            String message,
            Map<String, Object> result,
            OffsetDateTime now
    ) {
        switch (command.commandType()) {
            case PHOTO_PICKUP -> eventBus.publish(new PhotoPickupCommandFailedEvent(
                    command.agent().officeId(),
                    photoId(command),
                    command.id(),
                    message,
                    result,
                    now));
            case GENERATE_DOCUMENT -> eventBus.publish(new DocumentRenderCommandFailedEvent(
                    command.agent().officeId(),
                    documentJobId(command),
                    command.id(),
                    errorCode,
                    false,
                    message,
                    result,
                    now));
            case UPLOAD_DOCUMENT_ARTIFACT -> eventBus.publish(new DocumentDeliveryCommandFailedEvent(
                    command.agent().officeId(),
                    documentDeliveryRequestId(command),
                    command.id(),
                    message,
                    result,
                    now));
            case RELOAD_TEMPLATE -> {
                // No flow currently waits for RELOAD_TEMPLATE completion.
            }
        }
    }

    @Transactional
    public void ack(Long agentId, Long commandId) {
        var command = requireCommandForAgent(agentId, commandId);
        if (command.isTerminal()) {
            return;
        }
        command.ack(OffsetDateTime.now());
        recordCommandEvent(command, OperationEventSeverity.INFO, "AGENT_COMMAND_ACKED", "ArchDox Agent acknowledged command.");
        if (command.commandType() == ArchDoxAgentCommandType.PHOTO_PICKUP) {
            eventBus.publish(new PhotoPickupCommandAckedEvent(
                    command.agent().officeId(),
                    photoId(command),
                    command.id(),
                    OffsetDateTime.now()));
        }
        if (isDocumentRender(command)) {
            eventBus.publish(new DocumentRenderCommandAckedEvent(
                    command.agent().officeId(),
                    documentJobId(command),
                    command.id(),
                    OffsetDateTime.now()));
        }
        if (isDocumentArtifactDelivery(command)) {
            eventBus.publish(new DocumentDeliveryCommandAckedEvent(
                    command.agent().officeId(),
                    documentDeliveryRequestId(command),
                    command.id(),
                    OffsetDateTime.now()));
        }
    }

    @Transactional
    public void complete(Long agentId, Long commandId, Map<String, Object> result) {
        var command = requireCommandForAgent(agentId, commandId);
        if (command.isTerminal()) {
            return;
        }
        var now = OffsetDateTime.now();
        var safeResult = result == null ? Map.<String, Object>of() : result;
        command.complete(safeResult, now);
        recordCommandEvent(command, OperationEventSeverity.INFO, "AGENT_COMMAND_COMPLETED", "ArchDox Agent completed command.");
        if (command.commandType() == ArchDoxAgentCommandType.PHOTO_PICKUP) {
            eventBus.publish(new PhotoPickupCommandCompletedEvent(
                    command.agent().officeId(),
                    photoId(command),
                    command.id(),
                    safeResult,
                    now));
        }
        if (isDocumentRender(command)) {
            eventBus.publish(new DocumentRenderCommandCompletedEvent(
                    command.agent().officeId(),
                    documentJobId(command),
                    command.id(),
                    safeResult,
                    now));
        }
        if (isDocumentArtifactDelivery(command)) {
            eventBus.publish(new DocumentDeliveryCommandCompletedEvent(
                    command.agent().officeId(),
                    documentDeliveryRequestId(command),
                    command.id(),
                    safeResult,
                    now));
        }
    }

    @Transactional
    public void fail(
            Long agentId,
            Long commandId,
            String errorCode,
            Boolean retryable,
            String errorMessage,
            Map<String, Object> result
    ) {
        var command = requireCommandForAgent(agentId, commandId);
        if (command.isTerminal()) {
            return;
        }
        var now = OffsetDateTime.now();
        var message = errorMessage == null ? "Command failed" : errorMessage;
        var safeResult = failureResult(errorCode, retryable, message, result);
        command.fail(message, safeResult, now);
        recordCommandEvent(command, OperationEventSeverity.WARN, "AGENT_COMMAND_FAILED", message);
        if (command.commandType() == ArchDoxAgentCommandType.PHOTO_PICKUP) {
            eventBus.publish(new PhotoPickupCommandFailedEvent(
                    command.agent().officeId(),
                    photoId(command),
                    command.id(),
                    message,
                    safeResult,
                    now));
        }
        if (isDocumentRender(command)) {
            eventBus.publish(new DocumentRenderCommandFailedEvent(
                    command.agent().officeId(),
                    documentJobId(command),
                    command.id(),
                    firstText(errorCode, stringValue(safeResult.get("errorCode"))),
                    firstBoolean(retryable, safeResult.get("retryable")),
                    message,
                    safeResult,
                    now));
        }
        if (isDocumentArtifactDelivery(command)) {
            eventBus.publish(new DocumentDeliveryCommandFailedEvent(
                    command.agent().officeId(),
                    documentDeliveryRequestId(command),
                    command.id(),
                    message,
                    safeResult,
                    now));
        }
    }

    private void deliver(ArchDoxAgentCommand command) {
        var now = OffsetDateTime.now();
        if (sessionRegistry.send(
                command.agent().id(),
                AgentOutboundMessage.command(command.id(), command.commandType(), command.payloadJson()))) {
            command.markDelivered(now);
            return;
        }
        var disconnected = sessionRepository.markActiveSessionsDisconnectedForAgentAndApiInstance(
                command.agent().id(),
                properties.getApiInstanceId(),
                ArchDoxAgentSessionStatus.ACTIVE,
                ArchDoxAgentSessionStatus.DISCONNECTED,
                now,
                "No open WebSocket in API instance during command dispatch");
        if (disconnected > 0) {
            recordCommandEvent(
                    command,
                    OperationEventSeverity.WARN,
                    "AGENT_SESSION_STALE",
                    "Marked stale ArchDox Agent session disconnected during command dispatch.");
        }
    }

    private void deliverAfterCommit(Long commandId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deliverCommittedCommand(commandId);
                }
            });
            return;
        }
        deliverCommittedCommand(commandId);
    }

    private void deliverCommittedCommand(Long commandId) {
        commandDeliveryTransactionTemplate.executeWithoutResult(status -> commandRepository.findById(commandId)
                .filter(command -> command.status() == ArchDoxAgentCommandStatus.PENDING)
                .ifPresent(this::deliver));
    }

    private Map<String, Object> failureResult(
            String errorCode,
            Boolean retryable,
            String message,
            Map<String, Object> result
    ) {
        var safeResult = new LinkedHashMap<String, Object>(result == null ? Map.of() : result);
        if (errorCode != null && !errorCode.isBlank()) {
            safeResult.putIfAbsent("errorCode", errorCode);
        }
        if (retryable != null) {
            safeResult.putIfAbsent("retryable", retryable);
        }
        if (message != null && !message.isBlank()) {
            safeResult.putIfAbsent("message", message);
        }
        return safeResult;
    }

    private Optional<ArchDoxAgent> selectCommandTargetAgent(Long officeId, boolean allowLastKnownFallback) {
        var activeSessionAgent = sessionRepository
                .findByOfficeIdAndStatusOrderByLastSeenAtDesc(officeId, ArchDoxAgentSessionStatus.ACTIVE)
                .stream()
                .map(session -> session.agent())
                .findFirst();
        if (activeSessionAgent.isPresent()) {
            return activeSessionAgent;
        }
        var onlineAgent = agentRepository
                .findByOfficeIdAndStatusOrderByLastSeenAtDesc(officeId, ArchDoxAgentStatus.ONLINE)
                .stream()
                .findFirst();
        if (onlineAgent.isPresent() || !allowLastKnownFallback) {
            return onlineAgent;
        }
        return agentRepository.findFirstByOfficeIdOrderByLastSeenAtDesc(officeId);
    }

    private Optional<ArchDoxAgent> selectDocumentRenderTargetAgent(Long officeId, OutputFormat outputFormat) {
        var activeSessionAgent = sessionRepository
                .findByOfficeIdAndStatusOrderByLastSeenAtDesc(officeId, ArchDoxAgentSessionStatus.ACTIVE)
                .stream()
                .map(session -> session.agent())
                .filter(agent -> ArchDoxAgentCapabilities.supportsDocumentRender(agent, outputFormat))
                .findFirst();
        if (activeSessionAgent.isPresent()) {
            return activeSessionAgent;
        }
        return agentRepository
                .findByOfficeIdAndStatusOrderByLastSeenAtDesc(officeId, ArchDoxAgentStatus.ONLINE)
                .stream()
                .filter(agent -> ArchDoxAgentCapabilities.supportsDocumentRender(agent, outputFormat))
                .findFirst();
    }

    private boolean isDocumentRender(ArchDoxAgentCommand command) {
        return command.commandType() == ArchDoxAgentCommandType.GENERATE_DOCUMENT;
    }

    private boolean isDocumentArtifactDelivery(ArchDoxAgentCommand command) {
        return command.commandType() == ArchDoxAgentCommandType.UPLOAD_DOCUMENT_ARTIFACT;
    }

    private Long documentJobId(ArchDoxAgentCommand command) {
        return longValue(command.payloadJson().get("documentJobId"));
    }

    private Long documentDeliveryRequestId(ArchDoxAgentCommand command) {
        return longValue(command.payloadJson().get("deliveryRequestId"));
    }

    private Long photoId(ArchDoxAgentCommand command) {
        return longValue(command.payloadJson().get("photoId"));
    }

    private OutputFormat outputFormat(Map<String, Object> payload) {
        if (payload == null || payload.get("outputFormat") == null || String.valueOf(payload.get("outputFormat")).isBlank()) {
            return OutputFormat.DOCX;
        }
        return OutputFormat.valueOf(String.valueOf(payload.get("outputFormat")).trim().toUpperCase(java.util.Locale.ROOT));
    }

    private void recordCommandEvent(
            ArchDoxAgentCommand command,
            OperationEventSeverity severity,
            String eventType,
            String message
    ) {
        operationEventService.record(
                command.agent().officeId(),
                severity,
                eventType,
                commandWorkflowType(command),
                commandWorkflowKey(command),
                commandResourceType(command),
                commandResourceId(command),
                message,
                Map.of(
                        "commandId", command.id(),
                        "commandType", command.commandType().name(),
                        "agentId", command.agent().id(),
                        "status", command.status().name(),
                        "attemptCount", command.attemptCount()));
    }

    private String commandWorkflowType(ArchDoxAgentCommand command) {
        return switch (command.commandType()) {
            case PHOTO_PICKUP -> "photo-pickup";
            case GENERATE_DOCUMENT -> "document-generation";
            case UPLOAD_DOCUMENT_ARTIFACT -> "document-delivery";
            case RELOAD_TEMPLATE -> "agent-command";
        };
    }

    private String commandWorkflowKey(ArchDoxAgentCommand command) {
        return switch (command.commandType()) {
            case PHOTO_PICKUP -> "photo:" + photoId(command);
            case GENERATE_DOCUMENT -> "document-job:" + documentJobId(command);
            case UPLOAD_DOCUMENT_ARTIFACT -> "document-delivery:" + documentDeliveryRequestId(command);
            case RELOAD_TEMPLATE -> "agent-command:" + command.id();
        };
    }

    private String commandResourceType(ArchDoxAgentCommand command) {
        return switch (command.commandType()) {
            case PHOTO_PICKUP -> "PHOTO";
            case GENERATE_DOCUMENT -> "DOCUMENT_JOB";
            case UPLOAD_DOCUMENT_ARTIFACT -> "DOCUMENT_DELIVERY_REQUEST";
            case RELOAD_TEMPLATE -> "AGENT_COMMAND";
        };
    }

    private Long commandResourceId(ArchDoxAgentCommand command) {
        return switch (command.commandType()) {
            case PHOTO_PICKUP -> photoId(command);
            case GENERATE_DOCUMENT -> documentJobId(command);
            case UPLOAD_DOCUMENT_ARTIFACT -> documentDeliveryRequestId(command);
            case RELOAD_TEMPLATE -> command.id();
        };
    }

    private ArchDoxAgent requireAgent(Long agentId) {
        if (agentId == null) {
            throw new UnauthorizedException("Agent session is not registered");
        }
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new UnauthorizedException("Agent not found"));
    }

    private ArchDoxAgentCommand requireCommandForAgent(Long agentId, Long commandId) {
        if (agentId == null) {
            throw new UnauthorizedException("Agent session is not registered");
        }
        var command = commandRepository.findById(commandId)
                .orElseThrow(() -> new NotFoundException("Agent command not found"));
        if (!command.agent().id().equals(agentId)) {
            throw new UnauthorizedException("Command does not belong to this agent");
        }
        return command;
    }

    private String firstText(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String stringValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value);
    }

    private Boolean firstBoolean(Boolean first, Object second) {
        if (first != null) {
            return first;
        }
        if (second instanceof Boolean bool) {
            return bool;
        }
        if (second instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.valueOf(text);
        }
        throw new BadRequestException("Expected numeric value");
    }

    private ArchDoxAgentDeploymentMode deploymentMode(AgentHello hello) {
        if (hello.deploymentMode() == null || hello.deploymentMode().isBlank()) {
            return ArchDoxAgentDeploymentMode.LOCAL_OFFICE;
        }
        return ArchDoxAgentDeploymentMode.valueOf(hello.deploymentMode().trim().toUpperCase(java.util.Locale.ROOT));
    }
}
