package com.archdox.cloud.documenttype.domain;

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
@Table(name = "document_type_definitions")
public class DocumentTypeDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id")
    private Long officeId;

    @Column(nullable = false)
    private String code;

    @Column(name = "report_type", nullable = false)
    private String reportType;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String category;

    @Column(name = "default_template_code")
    private String defaultTemplateCode;

    @Column(name = "default_template_storage_ref")
    private String defaultTemplateStorageRef;

    @Column(name = "checklist_schema_code")
    private String checklistSchemaCode;

    @Column(name = "default_output_format", nullable = false)
    private String defaultOutputFormat;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "workflow_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> workflowJson = Map.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_layout_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> outputLayoutJson = Map.of();

    @Column(nullable = false)
    private boolean active;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DocumentTypeDefinition() {
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public String code() {
        return code;
    }

    public String reportType() {
        return reportType;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String category() {
        return category;
    }

    public String defaultTemplateCode() {
        return defaultTemplateCode;
    }

    public String defaultTemplateStorageRef() {
        return defaultTemplateStorageRef;
    }

    public String checklistSchemaCode() {
        return checklistSchemaCode;
    }

    public String defaultOutputFormat() {
        return defaultOutputFormat;
    }

    public Map<String, Object> workflowJson() {
        return workflowJson == null ? Map.of() : workflowJson;
    }

    public Map<String, Object> outputLayoutJson() {
        return outputLayoutJson == null ? Map.of() : outputLayoutJson;
    }

    public boolean active() {
        return active;
    }

    public int displayOrder() {
        return displayOrder;
    }
}
