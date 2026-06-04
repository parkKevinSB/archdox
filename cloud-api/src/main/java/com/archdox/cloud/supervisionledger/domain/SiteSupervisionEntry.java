package com.archdox.cloud.supervisionledger.domain;

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
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "site_supervision_entries")
public class SiteSupervisionEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "site_id", nullable = false)
    private Long siteId;

    @Column(name = "entry_date")
    private LocalDate entryDate;

    @Column(name = "floor_area")
    private String floorArea;

    @Column(name = "trade_code")
    private String tradeCode;

    @Column(name = "trade_name")
    private String tradeName;

    @Column(name = "process_code")
    private String processCode;

    @Column(name = "process_name")
    private String processName;

    @Column(name = "inspection_item_code")
    private String inspectionItemCode;

    @Column(name = "inspection_item_name")
    private String inspectionItemName;

    @Column(name = "supervision_content")
    private String supervisionContent;

    @Column(name = "result_status")
    private String resultStatus;

    @Column(name = "issue_text")
    private String issueText;

    @Column(name = "action_result")
    private String actionResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "photo_ids", columnDefinition = "jsonb", nullable = false)
    private List<Long> photoIds = List.of();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SiteSupervisionEntryStatus status;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_report_id", nullable = false)
    private Long sourceReportId;

    @Column(name = "source_report_revision", nullable = false)
    private int sourceReportRevision;

    @Column(name = "source_step_code", nullable = false)
    private String sourceStepCode;

    @Column(name = "source_step_client_revision", nullable = false)
    private int sourceStepClientRevision;

    @Column(name = "source_group_key")
    private String sourceGroupKey;

    @Column(name = "source_item_key")
    private String sourceItemKey;

    @Column(name = "source_entry_key", nullable = false)
    private String sourceEntryKey;

    @Column(name = "catalog_code")
    private String catalogCode;

    @Column(name = "catalog_version")
    private Integer catalogVersion;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SiteSupervisionEntry() {
    }

    public SiteSupervisionEntry(
            Long officeId,
            Long projectId,
            Long siteId,
            LocalDate entryDate,
            String floorArea,
            String tradeCode,
            String tradeName,
            String processCode,
            String processName,
            String inspectionItemCode,
            String inspectionItemName,
            String supervisionContent,
            String resultStatus,
            String issueText,
            String actionResult,
            List<Long> photoIds,
            SiteSupervisionEntryStatus status,
            String sourceType,
            Long sourceReportId,
            int sourceReportRevision,
            String sourceStepCode,
            int sourceStepClientRevision,
            String sourceGroupKey,
            String sourceItemKey,
            String sourceEntryKey,
            String catalogCode,
            Integer catalogVersion,
            Long createdBy,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.projectId = projectId;
        this.siteId = siteId;
        this.entryDate = entryDate;
        this.floorArea = floorArea;
        this.tradeCode = tradeCode;
        this.tradeName = tradeName;
        this.processCode = processCode;
        this.processName = processName;
        this.inspectionItemCode = inspectionItemCode;
        this.inspectionItemName = inspectionItemName;
        this.supervisionContent = supervisionContent;
        this.resultStatus = resultStatus;
        this.issueText = issueText;
        this.actionResult = actionResult;
        this.photoIds = photoIds == null ? List.of() : List.copyOf(photoIds);
        this.status = status;
        this.sourceType = sourceType;
        this.sourceReportId = sourceReportId;
        this.sourceReportRevision = sourceReportRevision;
        this.sourceStepCode = sourceStepCode;
        this.sourceStepClientRevision = sourceStepClientRevision;
        this.sourceGroupKey = sourceGroupKey;
        this.sourceItemKey = sourceItemKey;
        this.sourceEntryKey = sourceEntryKey;
        this.catalogCode = catalogCode;
        this.catalogVersion = catalogVersion;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
        this.createdAt = now;
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

    public LocalDate entryDate() {
        return entryDate;
    }

    public String floorArea() {
        return floorArea;
    }

    public String tradeCode() {
        return tradeCode;
    }

    public String tradeName() {
        return tradeName;
    }

    public String processCode() {
        return processCode;
    }

    public String processName() {
        return processName;
    }

    public String inspectionItemCode() {
        return inspectionItemCode;
    }

    public String inspectionItemName() {
        return inspectionItemName;
    }

    public String supervisionContent() {
        return supervisionContent;
    }

    public String resultStatus() {
        return resultStatus;
    }

    public String issueText() {
        return issueText;
    }

    public String actionResult() {
        return actionResult;
    }

    public List<Long> photoIds() {
        return photoIds;
    }

    public SiteSupervisionEntryStatus status() {
        return status;
    }

    public String sourceType() {
        return sourceType;
    }

    public Long sourceReportId() {
        return sourceReportId;
    }

    public int sourceReportRevision() {
        return sourceReportRevision;
    }

    public String sourceStepCode() {
        return sourceStepCode;
    }

    public int sourceStepClientRevision() {
        return sourceStepClientRevision;
    }

    public String sourceEntryKey() {
        return sourceEntryKey;
    }

    public String catalogCode() {
        return catalogCode;
    }

    public Integer catalogVersion() {
        return catalogVersion;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}
