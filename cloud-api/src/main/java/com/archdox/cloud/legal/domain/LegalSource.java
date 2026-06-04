package com.archdox.cloud.legal.domain;

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
@Table(name = "legal_sources")
public class LegalSource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "base_url")
    private String baseUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LegalSourceStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadataJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected LegalSource() {
    }

    public LegalSource(
            String code,
            String sourceType,
            String displayName,
            String baseUrl,
            Map<String, Object> metadataJson,
            OffsetDateTime now
    ) {
        this.code = required(code, "code");
        this.sourceType = required(sourceType, "sourceType");
        this.displayName = required(displayName, "displayName");
        this.baseUrl = blankToNull(baseUrl);
        this.status = LegalSourceStatus.ACTIVE;
        this.metadataJson = metadataJson == null ? Map.of() : Map.copyOf(metadataJson);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String sourceType, String displayName, String baseUrl, Map<String, Object> metadataJson, OffsetDateTime now) {
        this.sourceType = required(sourceType, "sourceType");
        this.displayName = required(displayName, "displayName");
        this.baseUrl = blankToNull(baseUrl);
        this.metadataJson = metadataJson == null ? Map.of() : Map.copyOf(metadataJson);
        this.status = LegalSourceStatus.ACTIVE;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String sourceType() {
        return sourceType;
    }

    public String displayName() {
        return displayName;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public LegalSourceStatus status() {
        return status;
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
