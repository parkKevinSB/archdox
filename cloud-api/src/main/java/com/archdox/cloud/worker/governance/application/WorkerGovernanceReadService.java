package com.archdox.cloud.worker.governance.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.infra.OperationEventRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.worker.governance.dto.WorkerActionDefinitionResponse;
import com.archdox.cloud.worker.governance.dto.WorkerGovernanceGroupResponse;
import com.archdox.cloud.worker.governance.dto.WorkerGovernanceSummaryResponse;
import com.archdox.worker.application.ArchDoxWorkerActionRegistry;
import com.archdox.worker.domain.ArchDoxWorkerActionDefinition;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerGovernanceReadService {
    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 30;
    private static final int DEFAULT_RECENT_LIMIT = 30;
    private static final int MAX_RECENT_LIMIT = 100;
    private static final String WORKFLOW_TYPE = "archdox-worker";
    private static final String EVENT_PREFIX = "ARCHDOX_WORKER_";

    private final PlatformAdminService platformAdminService;
    private final OperationEventRepository eventRepository;
    private final OperationEventService operationEventService;
    private final ArchDoxWorkerActionRegistry actionRegistry;

    public WorkerGovernanceReadService(
            PlatformAdminService platformAdminService,
            OperationEventRepository eventRepository,
            OperationEventService operationEventService,
            ArchDoxWorkerActionRegistry actionRegistry
    ) {
        this.platformAdminService = platformAdminService;
        this.eventRepository = eventRepository;
        this.operationEventService = operationEventService;
        this.actionRegistry = actionRegistry;
    }

    @Transactional(readOnly = true)
    public WorkerGovernanceSummaryResponse summary(
            UserPrincipal principal,
            Long officeId,
            Integer days,
            Integer recentLimit
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        var normalizedDays = normalizeDays(days);
        var to = OffsetDateTime.now();
        var from = to.minusDays(normalizedDays);
        var eventCounts = eventCounts(officeId, from, to);
        var requestReceived = count(eventCounts, "REQUEST_RECEIVED");
        var policyAllowed = count(eventCounts, "POLICY_ALLOWED");
        var policyDenied = count(eventCounts, "POLICY_DENIED");
        var approvalRequired = count(eventCounts, "APPROVAL_REQUIRED");
        var actionSucceeded = count(eventCounts, "ACTION_SUCCEEDED");
        var actionFailed = count(eventCounts, "ACTION_FAILED");
        var actionRejected = count(eventCounts, "ACTION_REJECTED");
        var actionUnknown = count(eventCounts, "ACTION_UNKNOWN");
        var totalTraceEvents = eventCounts.values().stream().mapToLong(Long::longValue).sum();
        var recentEvents = eventRepository.searchPlatformEvents(
                        officeId,
                        null,
                        WORKFLOW_TYPE,
                        null,
                        null,
                        null,
                        PageRequest.of(0, normalizeRecentLimit(recentLimit)))
                .stream()
                .map(operationEventService::toResponse)
                .toList();
        return new WorkerGovernanceSummaryResponse(
                from,
                to,
                officeId,
                normalizedDays,
                totalTraceEvents,
                requestReceived,
                policyAllowed,
                policyDenied,
                approvalRequired,
                actionSucceeded,
                actionFailed,
                actionRejected,
                actionUnknown,
                ratio(policyDenied + actionRejected + actionUnknown, requestReceived),
                ratio(approvalRequired, requestReceived),
                ratio(actionFailed, actionSucceeded + actionFailed),
                "No additional raw worker metric table is created. This view aggregates existing operation_events for up to 30 days and returns only bounded recent samples.",
                actionDefinitions(),
                eventTypeGroups(eventCounts),
                actionEventGroups(officeId, from, to),
                reasonGroups(officeId, from, to),
                recentEvents);
    }

    private List<WorkerActionDefinitionResponse> actionDefinitions() {
        return Arrays.stream(ArchDoxWorkerActionType.values())
                .map(actionType -> actionRegistry.definition(actionType).orElse(null))
                .filter(definition -> definition != null)
                .map(this::toActionDefinitionResponse)
                .toList();
    }

    private WorkerActionDefinitionResponse toActionDefinitionResponse(ArchDoxWorkerActionDefinition definition) {
        return new WorkerActionDefinitionResponse(
                definition.actionType().name(),
                definition.owner(),
                definition.executorName(),
                definition.enabled(),
                actionRegistry.resolve(definition.actionType()).isPresent(),
                definition.readOnly(),
                definition.riskLevel().name(),
                definition.requiresApprovalByDefault(),
                definition.supportsDryRun(),
                definition.allowedSources().stream().map(Enum::name).sorted().toList(),
                definition.requiredContextFields().stream().sorted().toList(),
                definition.description());
    }

    private Map<String, Long> eventCounts(Long officeId, OffsetDateTime from, OffsetDateTime to) {
        var counts = new LinkedHashMap<String, Long>();
        eventRepository.summarizeWorkerEventTypes(officeId, from, to)
                .forEach(row -> counts.put(shortEventType(row.getEventType()), number(row.getEventCount())));
        return counts;
    }

    private List<WorkerGovernanceGroupResponse> eventTypeGroups(Map<String, Long> eventCounts) {
        return eventCounts.entrySet().stream()
                .map(entry -> new WorkerGovernanceGroupResponse(null, entry.getKey(), null, entry.getValue()))
                .toList();
    }

    private List<WorkerGovernanceGroupResponse> actionEventGroups(Long officeId, OffsetDateTime from, OffsetDateTime to) {
        return eventRepository.summarizeWorkerActionEvents(officeId, from, to)
                .stream()
                .map(row -> new WorkerGovernanceGroupResponse(
                        safe(row.getActionType()),
                        shortEventType(row.getEventType()),
                        null,
                        number(row.getEventCount())))
                .toList();
    }

    private List<WorkerGovernanceGroupResponse> reasonGroups(Long officeId, OffsetDateTime from, OffsetDateTime to) {
        return eventRepository.summarizeWorkerReasons(officeId, from, to)
                .stream()
                .map(row -> new WorkerGovernanceGroupResponse(
                        null,
                        shortEventType(row.getEventType()),
                        safe(row.getReasonCode()),
                        number(row.getEventCount())))
                .toList();
    }

    private int normalizeDays(Integer days) {
        if (days == null) {
            return DEFAULT_DAYS;
        }
        if (days < 1) {
            throw new BadRequestException("days must be greater than zero");
        }
        return Math.min(days, MAX_DAYS);
    }

    private int normalizeRecentLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_RECENT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_RECENT_LIMIT));
    }

    private long count(Map<String, Long> counts, String eventType) {
        return counts.getOrDefault(eventType, 0L);
    }

    private long number(Long value) {
        return value == null ? 0L : value;
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0d;
        }
        return Math.round(((double) numerator / (double) denominator) * 10000.0d) / 100.0d;
    }

    private String shortEventType(String eventType) {
        var safe = safe(eventType);
        return safe.startsWith(EVENT_PREFIX) ? safe.substring(EVENT_PREFIX.length()) : safe;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value.trim();
    }
}
