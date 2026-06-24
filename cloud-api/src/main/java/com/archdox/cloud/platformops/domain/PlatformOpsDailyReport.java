package com.archdox.cloud.platformops.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "platform_ops_daily_reports")
public class PlatformOpsDailyReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "due_at", nullable = false)
    private OffsetDateTime dueAt;

    @Column(name = "period_from", nullable = false)
    private OffsetDateTime periodFrom;

    @Column(name = "period_to", nullable = false)
    private OffsetDateTime periodTo;

    @Column(nullable = false)
    private String status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlatformOpsFindingSeverity severity;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String summary;

    @Column(name = "report_path")
    private String reportPath;

    @Column(name = "ai_harness_run_id")
    private String aiHarnessRunId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "p_like_json", nullable = false, columnDefinition = "jsonb")
    private List<String> pLikeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "i_like_json", nullable = false, columnDefinition = "jsonb")
    private List<String> iLikeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "d_like_json", nullable = false, columnDefinition = "jsonb")
    private List<String> dLikeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommendations_json", nullable = false, columnDefinition = "jsonb")
    private List<String> recommendationsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> evidenceJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PlatformOpsDailyReport() {
    }

    public PlatformOpsDailyReport(
            Long runId,
            OffsetDateTime dueAt,
            OffsetDateTime periodFrom,
            OffsetDateTime periodTo,
            String status,
            PlatformOpsFindingSeverity severity,
            String title,
            String summary,
            String reportPath,
            String aiHarnessRunId,
            List<String> pLikeJson,
            List<String> iLikeJson,
            List<String> dLikeJson,
            List<String> recommendationsJson,
            Map<String, Object> evidenceJson,
            OffsetDateTime createdAt
    ) {
        this.runId = runId;
        this.dueAt = dueAt;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.status = blankToDefault(status, "WATCH");
        this.severity = severity == null ? PlatformOpsFindingSeverity.INFO : severity;
        this.title = blankToDefault(title, "ArchDox platform operations daily report");
        this.summary = blankToDefault(summary, "");
        this.reportPath = blankToNull(reportPath);
        this.aiHarnessRunId = blankToNull(aiHarnessRunId);
        this.pLikeJson = pLikeJson == null ? List.of() : List.copyOf(pLikeJson);
        this.iLikeJson = iLikeJson == null ? List.of() : List.copyOf(iLikeJson);
        this.dLikeJson = dLikeJson == null ? List.of() : List.copyOf(dLikeJson);
        this.recommendationsJson = recommendationsJson == null ? List.of() : List.copyOf(recommendationsJson);
        this.evidenceJson = evidenceJson == null ? Map.of() : Map.copyOf(evidenceJson);
        this.createdAt = createdAt;
    }

    public Long id() {
        return id;
    }

    public Long runId() {
        return runId;
    }

    public OffsetDateTime dueAt() {
        return dueAt;
    }

    public OffsetDateTime periodFrom() {
        return periodFrom;
    }

    public OffsetDateTime periodTo() {
        return periodTo;
    }

    public String status() {
        return status;
    }

    public PlatformOpsFindingSeverity severity() {
        return severity;
    }

    public String title() {
        return title;
    }

    public String summary() {
        return summary;
    }

    public String reportPath() {
        return reportPath;
    }

    public String aiHarnessRunId() {
        return aiHarnessRunId;
    }

    public List<String> pLikeJson() {
        return pLikeJson;
    }

    public List<String> iLikeJson() {
        return iLikeJson;
    }

    public List<String> dLikeJson() {
        return dLikeJson;
    }

    public List<String> recommendationsJson() {
        return recommendationsJson;
    }

    public Map<String, Object> evidenceJson() {
        return evidenceJson;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
