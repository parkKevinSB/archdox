package com.archdox.cloud.legal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "legal_versions")
public class LegalVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "act_id", nullable = false)
    private Long actId;

    @Column(name = "source_version_key", nullable = false)
    private String sourceVersionKey;

    @Column(name = "promulgation_date")
    private LocalDate promulgationDate;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_metadata_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> sourceMetadataJson;

    @Column(name = "captured_at", nullable = false)
    private OffsetDateTime capturedAt;

    protected LegalVersion() {
    }

    public LegalVersion(
            Long actId,
            String sourceVersionKey,
            LocalDate promulgationDate,
            LocalDate effectiveDate,
            String sourceUrl,
            String contentHash,
            Map<String, Object> sourceMetadataJson,
            OffsetDateTime capturedAt
    ) {
        this.actId = requireId(actId, "actId");
        this.sourceVersionKey = required(sourceVersionKey, "sourceVersionKey");
        this.promulgationDate = promulgationDate;
        this.effectiveDate = effectiveDate;
        this.sourceUrl = blankToNull(sourceUrl);
        this.contentHash = required(contentHash, "contentHash");
        this.sourceMetadataJson = sourceMetadataJson == null ? Map.of() : Map.copyOf(sourceMetadataJson);
        this.capturedAt = capturedAt;
    }

    public Long id() {
        return id;
    }

    public Long actId() {
        return actId;
    }

    public String sourceVersionKey() {
        return sourceVersionKey;
    }

    public LocalDate effectiveDate() {
        return effectiveDate;
    }

    public String contentHash() {
        return contentHash;
    }

    public OffsetDateTime capturedAt() {
        return capturedAt;
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
