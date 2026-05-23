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
@Table(name = "inspection_report_targets")
public class InspectionReportTarget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InspectionReportTargetRole role;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> snapshotJson = Map.of();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected InspectionReportTarget() {
    }

    public InspectionReportTarget(
            Long officeId,
            Long reportId,
            Long targetId,
            InspectionReportTargetRole role,
            Map<String, Object> snapshotJson,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.reportId = reportId;
        this.targetId = targetId;
        this.role = role;
        this.snapshotJson = snapshotJson == null ? Map.of() : Map.copyOf(snapshotJson);
        this.createdAt = now;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public Long reportId() {
        return reportId;
    }

    public Long targetId() {
        return targetId;
    }

    public InspectionReportTargetRole role() {
        return role;
    }

    public Map<String, Object> snapshotJson() {
        return snapshotJson;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }
}
