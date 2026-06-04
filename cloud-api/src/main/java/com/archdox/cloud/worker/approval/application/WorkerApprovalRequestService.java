package com.archdox.cloud.worker.approval.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.platformadmin.domain.PlatformAdminRole;
import com.archdox.cloud.worker.approval.domain.WorkerApprovalRequest;
import com.archdox.cloud.worker.approval.domain.WorkerApprovalRequestStatus;
import com.archdox.cloud.worker.approval.dto.WorkerApprovalRequestResponse;
import com.archdox.cloud.worker.approval.infra.WorkerApprovalRequestRepository;
import com.archdox.worker.application.ArchDoxWorkerTraceEvent;
import com.archdox.worker.application.ArchDoxWorkerTraceEventType;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionOrigin;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import com.archdox.worker.domain.ArchDoxWorkerRequestContext;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerApprovalRequestService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_TTL_DAYS = 7;
    private static final String PAYLOAD_APPROVAL_ID = "workerApprovalRequestId";

    private final WorkerApprovalRequestRepository repository;
    private final PlatformAdminService platformAdminService;
    private final OperationEventService operationEventService;

    public WorkerApprovalRequestService(
            WorkerApprovalRequestRepository repository,
            PlatformAdminService platformAdminService,
            OperationEventService operationEventService
    ) {
        this.repository = repository;
        this.platformAdminService = platformAdminService;
        this.operationEventService = operationEventService;
    }

    @Transactional
    public void createFromTrace(ArchDoxWorkerTraceEvent event) {
        if (event == null || event.eventType() != ArchDoxWorkerTraceEventType.APPROVAL_REQUIRED) {
            return;
        }
        var request = event.request();
        var action = event.action();
        if (request == null || action == null) {
            return;
        }
        if (repository.findByWorkerRequestIdAndActionType(request.requestId(), action.actionType()).isPresent()) {
            return;
        }
        var context = request.context();
        var requestedAt = OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC);
        var now = OffsetDateTime.now();
        var approval = new WorkerApprovalRequest(
                context.officeId(),
                request.requestId(),
                request.source(),
                request.command(),
                context.userId(),
                context.projectId(),
                context.siteId(),
                context.reportId(),
                context.documentJobId(),
                context.locale(),
                action.actionType(),
                action.origin(),
                action.reason(),
                action.confidence(),
                action.payload(),
                event.code(),
                event.message(),
                requestedAt,
                requestedAt.plusDays(DEFAULT_TTL_DAYS),
                now);
        repository.save(approval);
        operationEventService.record(
                context.officeId(),
                OperationEventSeverity.INFO,
                "ARCHDOX_WORKER_APPROVAL_REQUEST_CREATED",
                "archdox-worker-approval",
                approval.workflowKey(),
                "WORKER_APPROVAL_REQUEST",
                approval.id(),
                context.userId(),
                null,
                "Worker approval request was created.",
                Map.of(
                        "approvalRequestId", approval.id(),
                        "workerRequestId", request.requestId().toString(),
                        "actionType", action.actionType().name(),
                        "code", event.code()));
    }

    @Transactional(readOnly = true)
    public List<WorkerApprovalRequestResponse> list(
            UserPrincipal principal,
            Long officeId,
            String status,
            String actionType,
            Integer limit
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        var normalizedStatus = statusValue(status);
        var normalizedActionType = actionTypeValue(actionType);
        return repository.search(
                        officeId,
                        normalizedStatus,
                        normalizedActionType,
                        PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isApprovedExecution(ArchDoxWorkerRequest request, ArchDoxWorkerAction action) {
        if (request == null || action == null) {
            return false;
        }
        var approvalId = longValue(action.payload().get(PAYLOAD_APPROVAL_ID));
        if (approvalId == null) {
            return false;
        }
        return repository.findById(approvalId)
                .filter(approval -> approval.isApprovedFor(request.requestId(), action.actionType()))
                .filter(approval -> sameContext(approval, request.context()))
                .isPresent();
    }

    @Transactional
    public ApprovedWorkerActionSubmission approve(
            UserPrincipal principal,
            Long approvalRequestId,
            String reason
    ) {
        platformAdminService.requirePlatformAdmin(principal, PlatformAdminRole.SUPER_ADMIN, PlatformAdminRole.SUPPORT);
        var approval = requireApproval(approvalRequestId);
        if (approval.status() != WorkerApprovalRequestStatus.PENDING) {
            throw new BadRequestException(
                    "WORKER_APPROVAL_NOT_PENDING",
                    "errors.workerApproval.notPending",
                    "Worker approval request is not pending",
                    Map.of("approvalRequestId", approvalRequestId, "status", approval.status().name()));
        }
        var now = OffsetDateTime.now();
        var executionRequestId = UUID.randomUUID();
        approval.approve(principal.userId(), defaultReason(reason, "Approved by platform admin."), executionRequestId, now);
        operationEventService.record(
                approval.officeId(),
                OperationEventSeverity.INFO,
                "ARCHDOX_WORKER_APPROVAL_APPROVED",
                "archdox-worker-approval",
                approval.workflowKey(),
                "WORKER_APPROVAL_REQUEST",
                approval.id(),
                principal.userId(),
                null,
                "Worker approval request was approved.",
                Map.of(
                        "approvalRequestId", approval.id(),
                        "workerRequestId", approval.workerRequestId().toString(),
                        "executionRequestId", executionRequestId.toString(),
                        "actionType", approval.actionType().name()));
        return new ApprovedWorkerActionSubmission(
                toResponse(approval),
                approvedRequest(approval),
                approvedAction(approval, principal.userId()));
    }

    @Transactional
    public WorkerApprovalRequestResponse reject(
            UserPrincipal principal,
            Long approvalRequestId,
            String reason
    ) {
        platformAdminService.requirePlatformAdmin(principal, PlatformAdminRole.SUPER_ADMIN, PlatformAdminRole.SUPPORT);
        var approval = requireApproval(approvalRequestId);
        if (approval.status() != WorkerApprovalRequestStatus.PENDING) {
            throw new BadRequestException(
                    "WORKER_APPROVAL_NOT_PENDING",
                    "errors.workerApproval.notPending",
                    "Worker approval request is not pending",
                    Map.of("approvalRequestId", approvalRequestId, "status", approval.status().name()));
        }
        approval.reject(principal.userId(), defaultReason(reason, "Rejected by platform admin."), OffsetDateTime.now());
        operationEventService.record(
                approval.officeId(),
                OperationEventSeverity.INFO,
                "ARCHDOX_WORKER_APPROVAL_REJECTED",
                "archdox-worker-approval",
                approval.workflowKey(),
                "WORKER_APPROVAL_REQUEST",
                approval.id(),
                principal.userId(),
                null,
                "Worker approval request was rejected.",
                Map.of(
                        "approvalRequestId", approval.id(),
                        "workerRequestId", approval.workerRequestId().toString(),
                        "actionType", approval.actionType().name()));
        return toResponse(approval);
    }

    private WorkerApprovalRequest requireApproval(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "WORKER_APPROVAL_NOT_FOUND",
                        "errors.workerApproval.notFound",
                        "Worker approval request not found",
                        Map.of("approvalRequestId", id)));
    }

    private ArchDoxWorkerRequest approvedRequest(WorkerApprovalRequest approval) {
        return new ArchDoxWorkerRequest(
                approval.executionRequestId(),
                approval.requestSource(),
                approval.command(),
                new ArchDoxWorkerRequestContext(
                        approval.userId(),
                        approval.officeId(),
                        approval.projectId(),
                        approval.siteId(),
                        approval.reportId(),
                        approval.documentJobId(),
                        approval.locale()),
                Instant.now());
    }

    private ArchDoxWorkerAction approvedAction(WorkerApprovalRequest approval, Long approvedByUserId) {
        var payload = new LinkedHashMap<String, Object>(approval.actionPayloadJson());
        payload.put(PAYLOAD_APPROVAL_ID, approval.id());
        payload.put("workerApprovalDecision", "APPROVED");
        payload.put("workerApprovalApprovedByUserId", approvedByUserId);
        return new ArchDoxWorkerAction(
                approval.actionType(),
                payload,
                approval.actionReason() == null ? "Approved worker action execution." : approval.actionReason(),
                approval.confidence(),
                approval.actionOrigin() == null ? ArchDoxWorkerActionOrigin.SYSTEM : approval.actionOrigin());
    }

    private boolean sameContext(WorkerApprovalRequest approval, ArchDoxWorkerRequestContext context) {
        if (context == null) {
            return false;
        }
        return same(approval.officeId(), context.officeId())
                && same(approval.userId(), context.userId())
                && same(approval.projectId(), context.projectId())
                && same(approval.siteId(), context.siteId())
                && same(approval.reportId(), context.reportId())
                && same(approval.documentJobId(), context.documentJobId());
    }

    private boolean same(Long left, Long right) {
        return left == null ? right == null : left.equals(right);
    }

    private WorkerApprovalRequestStatus statusValue(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return WorkerApprovalRequestStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid worker approval status: " + status);
        }
    }

    private ArchDoxWorkerActionType actionTypeValue(String actionType) {
        if (actionType == null || actionType.isBlank()) {
            return null;
        }
        try {
            return ArchDoxWorkerActionType.valueOf(actionType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid worker action type: " + actionType);
        }
    }

    private int normalizeLimit(Integer limit) {
        return Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.parseLong(string);
        }
        return null;
    }

    private String defaultReason(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason.trim();
    }

    private WorkerApprovalRequestResponse toResponse(WorkerApprovalRequest approval) {
        return new WorkerApprovalRequestResponse(
                approval.id(),
                approval.officeId(),
                approval.status(),
                approval.workerRequestId(),
                approval.executionRequestId(),
                approval.requestSource(),
                approval.command(),
                approval.userId(),
                approval.projectId(),
                approval.siteId(),
                approval.reportId(),
                approval.documentJobId(),
                approval.locale(),
                approval.actionType(),
                approval.actionOrigin(),
                approval.actionReason(),
                approval.confidence(),
                approval.actionPayloadJson(),
                approval.decisionCode(),
                approval.decisionMessage(),
                approval.decidedByUserId(),
                approval.decisionReason(),
                approval.requestedAt(),
                approval.expiresAt(),
                approval.decidedAt(),
                approval.createdAt(),
                approval.updatedAt());
    }
}
