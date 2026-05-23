package com.archdox.cloud.checklist.domain;

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
@Table(name = "checklist_schemas")
public class ChecklistSchema {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id")
    private Long officeId;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @Column(name = "site_type")
    private String siteType;

    @Column(name = "target_type")
    private String targetType;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChecklistSchemaStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "schema_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> schemaJson = Map.of();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ChecklistSchema() {
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public String reportType() {
        return reportType;
    }

    public String siteType() {
        return siteType;
    }

    public String targetType() {
        return targetType;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public int version() {
        return version;
    }

    public ChecklistSchemaStatus status() {
        return status;
    }

    public Map<String, Object> schemaJson() {
        return schemaJson;
    }
}
