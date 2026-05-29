package com.archdox.cloud.reportai.domain;

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
@Table(name = "report_preflight_review_findings")
public class ReportPreflightReviewFinding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "review_run_id", nullable = false)
    private Long reviewRunId;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String severity;

    private String location;

    @Column(nullable = false)
    private String message;

    private String evidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> attributesJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_status", nullable = false)
    private ReportPreflightFindingResolutionStatus resolutionStatus = ReportPreflightFindingResolutionStatus.OPEN;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ReportPreflightReviewFinding() {
    }

    public ReportPreflightReviewFinding(
            Long officeId,
            Long reviewRunId,
            Long reportId,
            String source,
            String code,
            String severity,
            String location,
            String message,
            String evidence,
            Map<String, String> attributesJson,
            OffsetDateTime createdAt
    ) {
        this.officeId = officeId;
        this.reviewRunId = reviewRunId;
        this.reportId = reportId;
        this.source = source;
        this.code = code;
        this.severity = severity;
        this.location = location;
        this.message = message;
        this.evidence = evidence;
        this.attributesJson = attributesJson == null ? Map.of() : Map.copyOf(attributesJson);
        this.resolutionStatus = ReportPreflightFindingResolutionStatus.OPEN;
        this.createdAt = createdAt;
    }

    public void resolve(ReportPreflightFindingResolutionStatus status, String note, Long resolvedBy, OffsetDateTime now) {
        this.resolutionStatus = status == null ? ReportPreflightFindingResolutionStatus.OPEN : status;
        this.resolutionNote = note == null || note.isBlank() ? null : note.trim();
        if (this.resolutionStatus == ReportPreflightFindingResolutionStatus.OPEN) {
            this.resolvedBy = null;
            this.resolvedAt = null;
            return;
        }
        this.resolvedBy = resolvedBy;
        this.resolvedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public Long reviewRunId() {
        return reviewRunId;
    }

    public Long reportId() {
        return reportId;
    }

    public String source() {
        return source;
    }

    public String code() {
        return code;
    }

    public String severity() {
        return severity;
    }

    public String location() {
        return location;
    }

    public String message() {
        return message;
    }

    public String evidence() {
        return evidence;
    }

    public Map<String, String> attributesJson() {
        return attributesJson;
    }

    public ReportPreflightFindingResolutionStatus resolutionStatus() {
        return resolutionStatus;
    }

    public String resolutionNote() {
        return resolutionNote;
    }

    public Long resolvedBy() {
        return resolvedBy;
    }

    public OffsetDateTime resolvedAt() {
        return resolvedAt;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }
}
