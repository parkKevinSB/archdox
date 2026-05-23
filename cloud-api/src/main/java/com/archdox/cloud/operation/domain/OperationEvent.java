package com.archdox.cloud.operation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "operation_events")
public class OperationEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id")
    private Long officeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperationEventSeverity severity;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "workflow_type")
    private String workflowType;

    @Column(name = "workflow_key")
    private String workflowKey;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(nullable = false)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payloadJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected OperationEvent() {
    }

    public OperationEvent(
            Long officeId,
            OperationEventSeverity severity,
            String eventType,
            String workflowType,
            String workflowKey,
            String resourceType,
            String resourceId,
            Long actorUserId,
            String correlationId,
            String message,
            Map<String, Object> payloadJson,
            OffsetDateTime createdAt
    ) {
        this.officeId = officeId;
        this.severity = severity;
        this.eventType = eventType;
        this.workflowType = workflowType;
        this.workflowKey = workflowKey;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.actorUserId = actorUserId;
        this.correlationId = correlationId;
        this.message = message;
        this.payloadJson = payloadJson == null ? Map.of() : Map.copyOf(payloadJson);
        this.createdAt = createdAt;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public OperationEventSeverity severity() {
        return severity;
    }

    public String eventType() {
        return eventType;
    }

    public String workflowType() {
        return workflowType;
    }

    public String workflowKey() {
        return workflowKey;
    }

    public String resourceType() {
        return resourceType;
    }

    public String resourceId() {
        return resourceId;
    }

    public Long actorUserId() {
        return actorUserId;
    }

    public String correlationId() {
        return correlationId;
    }

    public String message() {
        return message;
    }

    public Map<String, Object> payloadJson() {
        return payloadJson;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }
}
