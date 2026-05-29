package com.archdox.cloud.documentai.domain;

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
@Table(name = "document_ai_review_findings")
public class DocumentAiReviewFinding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "review_run_id", nullable = false)
    private Long reviewRunId;

    @Column(name = "document_job_id", nullable = false)
    private Long documentJobId;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

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

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected DocumentAiReviewFinding() {
    }

    public DocumentAiReviewFinding(
            Long officeId,
            Long reviewRunId,
            Long documentJobId,
            Long reportId,
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
        this.documentJobId = documentJobId;
        this.reportId = reportId;
        this.code = code;
        this.severity = severity;
        this.location = location;
        this.message = message;
        this.evidence = evidence;
        this.attributesJson = attributesJson == null ? Map.of() : Map.copyOf(attributesJson);
        this.createdAt = createdAt;
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

    public Long documentJobId() {
        return documentJobId;
    }

    public Long reportId() {
        return reportId;
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

    public OffsetDateTime createdAt() {
        return createdAt;
    }
}
