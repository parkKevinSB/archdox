package com.archdox.cloud.inspectiontarget.domain;

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
@Table(name = "inspection_targets")
public class InspectionTarget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "site_id", nullable = false)
    private Long siteId;

    @Column(name = "parent_target_id")
    private Long parentTargetId;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    private String code;

    @Column(nullable = false)
    private String name;

    private String address;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadataJson = Map.of();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InspectionTargetStatus status = InspectionTargetStatus.ACTIVE;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected InspectionTarget() {
    }

    public InspectionTarget(
            Long officeId,
            Long projectId,
            Long siteId,
            Long parentTargetId,
            String targetType,
            String code,
            String name,
            String address,
            Map<String, Object> metadataJson,
            Long createdBy,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.projectId = projectId;
        this.siteId = siteId;
        this.parentTargetId = parentTargetId;
        this.targetType = targetType;
        this.code = code;
        this.name = name;
        this.address = address;
        this.metadataJson = metadataJson == null ? Map.of() : Map.copyOf(metadataJson);
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void archive(OffsetDateTime now) {
        this.status = InspectionTargetStatus.ARCHIVED;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public Long projectId() {
        return projectId;
    }

    public Long siteId() {
        return siteId;
    }

    public Long parentTargetId() {
        return parentTargetId;
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

    public String address() {
        return address;
    }

    public Map<String, Object> metadataJson() {
        return metadataJson;
    }

    public InspectionTargetStatus status() {
        return status;
    }
}
