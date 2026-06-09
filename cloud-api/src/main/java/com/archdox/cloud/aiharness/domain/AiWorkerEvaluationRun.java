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
@Table(name = "ai_worker_evaluation_runs")
public class AiWorkerEvaluationRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_key", nullable = false, unique = true)
    private String runKey;

    @Column(name = "trigger_type", nullable = false)
    private String triggerType;

    @Column(nullable = false)
    private String status;

    @Column(name = "evaluation_mode", nullable = false)
    private String evaluationMode;

    @Column(name = "total_cases", nullable = false)
    private int totalCases;

    @Column(name = "automated_cases", nullable = false)
    private int automatedCases;

    @Column(name = "passed_cases", nullable = false)
    private int passedCases;

    @Column(name = "warning_cases", nullable = false)
    private int warningCases;

    @Column(name = "failed_cases", nullable = false)
    private int failedCases;

    @Column(name = "pass_rate_percent", nullable = false)
    private int passRatePercent;

    @Column(name = "group_count", nullable = false)
    private int groupCount;

    @Column(name = "signal_count", nullable = false)
    private int signalCount;

    @Column(name = "warning_signal_count", nullable = false)
    private int warningSignalCount;

    @Column(name = "failed_signal_count", nullable = false)
    private int failedSignalCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> summaryJson;

    @Column(name = "triggered_by_user_id")
    private Long triggeredByUserId;

    @Column(name = "triggered_by_email")
    private String triggeredByEmail;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at", nullable = false)
    private OffsetDateTime completedAt;

    protected AiWorkerEvaluationRun() {
    }

    public AiWorkerEvaluationRun(
            String runKey,
            String triggerType,
            String status,
            String evaluationMode,
            int totalCases,
            int automatedCases,
            int passedCases,
            int warningCases,
            int failedCases,
            int passRatePercent,
            int groupCount,
            int signalCount,
            int warningSignalCount,
            int failedSignalCount,
            Map<String, Object> summaryJson,
            Long triggeredByUserId,
            String triggeredByEmail,
            OffsetDateTime now
    ) {
        var createdAtValue = now == null ? OffsetDateTime.now() : now;
        this.runKey = required(runKey, "runKey");
        this.triggerType = required(triggerType, "triggerType");
        this.status = required(status, "status");
        this.evaluationMode = required(evaluationMode, "evaluationMode");
        this.totalCases = totalCases;
        this.automatedCases = automatedCases;
        this.passedCases = passedCases;
        this.warningCases = warningCases;
        this.failedCases = failedCases;
        this.passRatePercent = passRatePercent;
        this.groupCount = groupCount;
        this.signalCount = signalCount;
        this.warningSignalCount = warningSignalCount;
        this.failedSignalCount = failedSignalCount;
        this.summaryJson = summaryJson == null ? Map.of() : Map.copyOf(summaryJson);
        this.triggeredByUserId = triggeredByUserId;
        this.triggeredByEmail = blankToNull(triggeredByEmail);
        this.createdAt = createdAtValue;
        this.completedAt = createdAtValue;
    }

    public Long id() {
        return id;
    }

    public String runKey() {
        return runKey;
    }

    public String triggerType() {
        return triggerType;
    }

    public String status() {
        return status;
    }

    public String evaluationMode() {
        return evaluationMode;
    }

    public int totalCases() {
        return totalCases;
    }

    public int automatedCases() {
        return automatedCases;
    }

    public int passedCases() {
        return passedCases;
    }

    public int warningCases() {
        return warningCases;
    }

    public int failedCases() {
        return failedCases;
    }

    public int passRatePercent() {
        return passRatePercent;
    }

    public int groupCount() {
        return groupCount;
    }

    public int signalCount() {
        return signalCount;
    }

    public int warningSignalCount() {
        return warningSignalCount;
    }

    public int failedSignalCount() {
        return failedSignalCount;
    }

    public Map<String, Object> summaryJson() {
        return summaryJson;
    }

    public Long triggeredByUserId() {
        return triggeredByUserId;
    }

    public String triggeredByEmail() {
        return triggeredByEmail;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime completedAt() {
        return completedAt;
    }

    private static String required(String value, String fieldName) {
        var normalized = blankToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
