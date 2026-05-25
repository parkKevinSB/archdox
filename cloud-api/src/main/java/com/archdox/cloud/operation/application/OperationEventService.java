package com.archdox.cloud.operation.application;

import com.archdox.cloud.global.logging.CorrelationIds;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.operation.domain.OperationEvent;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.operation.dto.OperationEventResponse;
import com.archdox.cloud.operation.infra.OperationEventRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationEventService {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final OperationEventRepository repository;

    public OperationEventService(OperationEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            Long officeId,
            OperationEventSeverity severity,
            String eventType,
            String workflowType,
            String workflowKey,
            String resourceType,
            Object resourceId,
            String message,
            Map<String, Object> payload
    ) {
        record(
                officeId,
                severity,
                eventType,
                workflowType,
                workflowKey,
                resourceType,
                resourceId,
                null,
                null,
                message,
                payload);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            Long officeId,
            OperationEventSeverity severity,
            String eventType,
            String workflowType,
            String workflowKey,
            String resourceType,
            Object resourceId,
            Long actorUserId,
            String correlationId,
            String message,
            Map<String, Object> payload
    ) {
        repository.save(new OperationEvent(
                officeId,
                severity == null ? OperationEventSeverity.INFO : severity,
                required(eventType, "eventType"),
                blankToNull(workflowType),
                blankToNull(workflowKey),
                blankToNull(resourceType),
                resourceId == null ? null : String.valueOf(resourceId),
                actorUserId,
                effectiveCorrelationId(correlationId),
                required(message, "message"),
                payload == null ? Map.of() : payload,
                OffsetDateTime.now()));
    }

    @Transactional(readOnly = true)
    public List<OperationEventResponse> list(
            String eventType,
            String workflowType,
            String workflowKey,
            String resourceType,
            String resourceId,
            Integer limit
    ) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var size = Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));
        return repository.searchOfficeEvents(
                        officeId,
                        blankToNull(eventType),
                        blankToNull(workflowType),
                        blankToNull(workflowKey),
                        blankToNull(resourceType),
                        blankToNull(resourceId),
                        PageRequest.of(0, size))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public OperationEventResponse toResponse(OperationEvent event) {
        return new OperationEventResponse(
                event.id(),
                event.officeId(),
                event.severity(),
                event.eventType(),
                event.workflowType(),
                event.workflowKey(),
                event.resourceType(),
                event.resourceId(),
                event.actorUserId(),
                event.correlationId(),
                event.message(),
                event.payloadJson(),
                event.createdAt());
    }

    private String required(String value, String fieldName) {
        var normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String effectiveCorrelationId(String correlationId) {
        var explicit = blankToNull(correlationId);
        return explicit == null ? blankToNull(CorrelationIds.current()) : explicit;
    }
}
