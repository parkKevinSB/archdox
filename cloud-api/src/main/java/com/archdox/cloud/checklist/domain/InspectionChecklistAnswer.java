package com.archdox.cloud.checklist.domain;

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
@Table(name = "inspection_checklist_answers")
public class InspectionChecklistAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "checklist_schema_id", nullable = false)
    private Long checklistSchemaId;

    @Column(name = "checklist_item_id", nullable = false)
    private Long checklistItemId;

    @Column(name = "target_id")
    private Long targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_value_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> answerValueJson = Map.of();

    private String note;

    @Column(name = "client_revision", nullable = false)
    private int clientRevision = 1;

    @Column(name = "saved_by")
    private Long savedBy;

    @Column(name = "saved_at", nullable = false)
    private OffsetDateTime savedAt;

    protected InspectionChecklistAnswer() {
    }

    public InspectionChecklistAnswer(
            Long officeId,
            Long reportId,
            Long checklistSchemaId,
            Long checklistItemId,
            Long targetId,
            Map<String, Object> answerValueJson,
            String note,
            Long savedBy,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.reportId = reportId;
        this.checklistSchemaId = checklistSchemaId;
        this.checklistItemId = checklistItemId;
        this.targetId = targetId;
        this.answerValueJson = answerValueJson == null ? Map.of() : Map.copyOf(answerValueJson);
        this.note = note;
        this.savedBy = savedBy;
        this.savedAt = now;
    }

    public void update(Map<String, Object> answerValueJson, String note, Long savedBy, OffsetDateTime now) {
        this.answerValueJson = answerValueJson == null ? Map.of() : Map.copyOf(answerValueJson);
        this.note = note;
        this.savedBy = savedBy;
        this.savedAt = now;
        this.clientRevision++;
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

    public Long checklistSchemaId() {
        return checklistSchemaId;
    }

    public Long checklistItemId() {
        return checklistItemId;
    }

    public Long targetId() {
        return targetId;
    }

    public Map<String, Object> answerValueJson() {
        return answerValueJson;
    }

    public String note() {
        return note;
    }

    public int clientRevision() {
        return clientRevision;
    }

    public OffsetDateTime savedAt() {
        return savedAt;
    }
}
