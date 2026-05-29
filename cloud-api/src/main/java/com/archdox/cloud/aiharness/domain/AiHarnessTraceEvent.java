package com.archdox.cloud.aiharness.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_harness_trace_events")
public class AiHarnessTraceEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id")
    private Long officeId;

    @Column(name = "harness_run_id", nullable = false)
    private String harnessRunId;

    @Column(name = "harness_id", nullable = false)
    private String harnessId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column
    private String status;

    @Column
    private Integer attempt;

    @Column(name = "model_id")
    private String modelId;

    @Column(name = "call_id")
    private String callId;

    @Column(name = "prompt_id")
    private String promptId;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "finding_count")
    private Integer findingCount;

    @Column(name = "validation_valid")
    private Boolean validationValid;

    @Column(name = "validation_error_count")
    private Integer validationErrorCount;

    @Column(name = "error_type")
    private String errorType;

    @Column
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> attributesJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AiHarnessTraceEvent() {
    }

    public AiHarnessTraceEvent(
            Long officeId,
            String harnessRunId,
            String harnessId,
            String eventType,
            String status,
            Integer attempt,
            String modelId,
            String callId,
            String promptId,
            String promptVersion,
            Integer inputTokens,
            Integer outputTokens,
            Long latencyMs,
            Integer findingCount,
            Boolean validationValid,
            Integer validationErrorCount,
            String errorType,
            String message,
            Map<String, Object> attributesJson,
            OffsetDateTime createdAt
    ) {
        this.officeId = officeId;
        this.harnessRunId = required(harnessRunId, "harnessRunId");
        this.harnessId = required(harnessId, "harnessId");
        this.eventType = required(eventType, "eventType");
        this.status = blankToNull(status);
        this.attempt = attempt;
        this.modelId = blankToNull(modelId);
        this.callId = blankToNull(callId);
        this.promptId = blankToNull(promptId);
        this.promptVersion = blankToNull(promptVersion);
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.findingCount = findingCount;
        this.validationValid = validationValid;
        this.validationErrorCount = validationErrorCount;
        this.errorType = blankToNull(errorType);
        this.message = blankToNull(message);
        this.attributesJson = attributesJson == null ? Map.of() : Map.copyOf(attributesJson);
        this.createdAt = createdAt == null ? OffsetDateTime.now() : createdAt;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public String harnessRunId() {
        return harnessRunId;
    }

    public String harnessId() {
        return harnessId;
    }

    public String eventType() {
        return eventType;
    }

    public String status() {
        return status;
    }

    public Integer attempt() {
        return attempt;
    }

    public String modelId() {
        return modelId;
    }

    public String callId() {
        return callId;
    }

    public String promptId() {
        return promptId;
    }

    public String promptVersion() {
        return promptVersion;
    }

    public Integer inputTokens() {
        return inputTokens;
    }

    public Integer outputTokens() {
        return outputTokens;
    }

    public Long latencyMs() {
        return latencyMs;
    }

    public Integer findingCount() {
        return findingCount;
    }

    public Boolean validationValid() {
        return validationValid;
    }

    public Integer validationErrorCount() {
        return validationErrorCount;
    }

    public String errorType() {
        return errorType;
    }

    public String message() {
        return message;
    }

    public Map<String, Object> attributesJson() {
        return attributesJson;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
