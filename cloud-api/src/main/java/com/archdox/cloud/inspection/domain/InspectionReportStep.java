package com.archdox.cloud.inspection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "inspection_report_steps")
public class InspectionReportStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private InspectionReport report;

    @Column(name = "step_code", nullable = false)
    private String stepCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "payload_storage_mode", nullable = false)
    private PayloadStorageMode payloadStorageMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "jsonb")
    private Map<String, Object> payloadJson;

    @Column(name = "local_draft_ref")
    private String localDraftRef;

    @Column(name = "client_revision", nullable = false)
    private int clientRevision;

    @Column(name = "saved_by")
    private Long savedBy;

    @Column(name = "saved_at", nullable = false)
    private OffsetDateTime savedAt;

    protected InspectionReportStep() {
    }

    public InspectionReportStep(
            InspectionReport report,
            String stepCode,
            PayloadStorageMode payloadStorageMode,
            Map<String, Object> payloadJson,
            Long savedBy,
            OffsetDateTime now
    ) {
        this.report = report;
        this.stepCode = stepCode;
        this.payloadStorageMode = payloadStorageMode;
        this.payloadJson = payloadJson;
        this.savedBy = savedBy;
        this.savedAt = now;
        this.clientRevision = 1;
    }

    public void update(Map<String, Object> payloadJson, Long savedBy, OffsetDateTime now) {
        this.payloadJson = payloadJson;
        this.savedBy = savedBy;
        this.savedAt = now;
        this.clientRevision++;
    }

    public String stepCode() {
        return stepCode;
    }

    public PayloadStorageMode payloadStorageMode() {
        return payloadStorageMode;
    }

    public Map<String, Object> payloadJson() {
        return payloadJson;
    }

    public int clientRevision() {
        return clientRevision;
    }

    public OffsetDateTime savedAt() {
        return savedAt;
    }
}
