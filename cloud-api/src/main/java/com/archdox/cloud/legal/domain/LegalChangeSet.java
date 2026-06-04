package com.archdox.cloud.legal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "legal_change_sets")
public class LegalChangeSet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "act_id", nullable = false)
    private Long actId;

    @Column(name = "sync_run_id")
    private Long syncRunId;

    @Column(name = "previous_version_id")
    private Long previousVersionId;

    @Column(name = "new_version_id", nullable = false)
    private Long newVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LegalChangeSetStatus status;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "detected_at", nullable = false)
    private OffsetDateTime detectedAt;

    @Column(nullable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadataJson;

    protected LegalChangeSet() {
    }

    public LegalChangeSet(
            Long actId,
            Long syncRunId,
            Long previousVersionId,
            Long newVersionId,
            LocalDate effectiveDate,
            String summary,
            Map<String, Object> metadataJson,
            OffsetDateTime now
    ) {
        this.actId = requireId(actId, "actId");
        this.syncRunId = syncRunId;
        this.previousVersionId = previousVersionId;
        this.newVersionId = requireId(newVersionId, "newVersionId");
        this.status = LegalChangeSetStatus.RECORDED;
        this.effectiveDate = effectiveDate;
        this.detectedAt = now;
        this.summary = required(summary, "summary");
        this.metadataJson = metadataJson == null ? Map.of() : Map.copyOf(metadataJson);
    }

    public Long id() {
        return id;
    }

    public Long actId() {
        return actId;
    }

    public Long syncRunId() {
        return syncRunId;
    }

    public Long previousVersionId() {
        return previousVersionId;
    }

    public Long newVersionId() {
        return newVersionId;
    }

    public LegalChangeSetStatus status() {
        return status;
    }

    public LocalDate effectiveDate() {
        return effectiveDate;
    }

    public OffsetDateTime detectedAt() {
        return detectedAt;
    }

    public String summary() {
        return summary;
    }

    private static Long requireId(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
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
