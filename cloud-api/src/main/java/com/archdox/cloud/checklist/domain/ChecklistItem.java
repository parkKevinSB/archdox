package com.archdox.cloud.checklist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "checklist_items")
public class ChecklistItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "checklist_schema_id", nullable = false)
    private Long checklistSchemaId;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(nullable = false)
    private String label;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_type", nullable = false)
    private ChecklistAnswerType answerType;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_json", nullable = false, columnDefinition = "jsonb")
    private List<String> optionsJson = List.of();

    protected ChecklistItem() {
    }

    public Long id() {
        return id;
    }

    public Long checklistSchemaId() {
        return checklistSchemaId;
    }

    public String itemCode() {
        return itemCode;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public ChecklistAnswerType answerType() {
        return answerType;
    }

    public boolean required() {
        return required;
    }

    public int displayOrder() {
        return displayOrder;
    }

    public List<String> optionsJson() {
        return optionsJson;
    }
}
