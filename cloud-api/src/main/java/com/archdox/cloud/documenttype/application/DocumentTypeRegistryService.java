package com.archdox.cloud.documenttype.application;

import com.archdox.cloud.documenttype.domain.DocumentTypeDefinition;
import com.archdox.cloud.documenttype.dto.DocumentTypeResponse;
import com.archdox.cloud.documenttype.infra.DocumentTypeDefinitionRepository;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.inspection.dto.ReportWorkflowFieldResponse;
import com.archdox.cloud.inspection.dto.ReportWorkflowStepResponse;
import com.archdox.cloud.office.application.OfficeContext;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentTypeRegistryService {
    private static final List<String> CURRENT_CONSTRUCTION_SUPERVISION_TYPES = List.of(
            "CONSTRUCTION_DAILY_SUPERVISION_LOG",
            "CONSTRUCTION_SUPERVISION_REPORT",
            "CONSTRUCTION_SUPERVISION_CHECKLIST");

    private final DocumentTypeDefinitionRepository repository;

    public DocumentTypeRegistryService(DocumentTypeDefinitionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DocumentTypeResponse> listVisible() {
        OfficeContext.requireCurrentOfficeId();
        return repository.findSystemVisible().stream()
                .filter(this::isUserCreatable)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentTypeResponse get(String code) {
        OfficeContext.requireCurrentOfficeId();
        return toResponse(resolveUserCreatable(code)
                .orElseThrow(() -> new NotFoundException(
                        "DOCUMENT_TYPE_NOT_FOUND",
                        "errors.documentType.notFound",
                        "Document type not found",
                        Map.of("documentType", code))));
    }

    @Transactional(readOnly = true)
    public DocumentTypeDefinition requireForReportCreation(Long officeId, String code) {
        return resolveUserCreatable(code)
                .orElseThrow(() -> new BadRequestException(
                        "DOCUMENT_TYPE_NOT_SUPPORTED",
                        "errors.documentType.notSupported",
                        "Document type is not supported: " + code,
                        Map.of("documentType", code)));
    }

    @Transactional(readOnly = true)
    public Optional<DocumentTypeDefinition> resolveByReportType(Long officeId, String reportType) {
        return resolve(officeId, reportType);
    }

    public List<ReportWorkflowStepResponse> workflowSteps(DocumentTypeDefinition definition) {
        var rawSteps = definition.workflowJson().get("steps");
        if (!(rawSteps instanceof List<?> steps)) {
            return List.of();
        }
        return steps.stream()
                .filter(Map.class::isInstance)
                .map(rawStep -> parseStep(castMap(rawStep)))
                .filter(Objects::nonNull)
                .toList();
    }

    public String workflowId(DocumentTypeDefinition definition) {
        return textOrDefault(definition.workflowJson().get("flowId"), definition.code().toLowerCase(Locale.ROOT));
    }

    public String workflowTitle(DocumentTypeDefinition definition) {
        return textOrDefault(definition.workflowJson().get("title"), definition.name());
    }

    public DocumentTypeResponse toResponse(DocumentTypeDefinition definition) {
        return new DocumentTypeResponse(
                definition.id(),
                definition.officeId(),
                definition.code(),
                definition.reportType(),
                definition.name(),
                definition.description(),
                definition.category(),
                definition.defaultTemplateCode(),
                definition.defaultTemplateStorageRef(),
                definition.checklistSchemaCode(),
                definition.defaultOutputFormat(),
                definition.displayOrder(),
                workflowSteps(definition));
    }

    private Optional<DocumentTypeDefinition> resolve(Long officeId, String code) {
        var normalized = normalizeCode(code);
        if (normalized == null) {
            return Optional.empty();
        }
        return repository.resolve(officeId, normalized);
    }

    private Optional<DocumentTypeDefinition> resolveUserCreatable(String code) {
        var normalized = normalizeCode(code);
        if (normalized == null) {
            return Optional.empty();
        }
        return repository.findSystemResolutionCandidates(normalized).stream()
                .filter(this::isUserCreatable)
                .findFirst();
    }

    private boolean isUserCreatable(DocumentTypeDefinition definition) {
        return definition.officeId() == null
                && "CONSTRUCTION_SUPERVISION".equals(normalizeCode(definition.category()))
                && CURRENT_CONSTRUCTION_SUPERVISION_TYPES.contains(normalizeCode(definition.code()))
                && CURRENT_CONSTRUCTION_SUPERVISION_TYPES.contains(normalizeCode(definition.reportType()));
    }

    private ReportWorkflowStepResponse parseStep(Map<String, Object> rawStep) {
        var code = normalizeCode(rawStep.get("code"));
        var title = text(rawStep.get("title"));
        if (code == null || title == null) {
            return null;
        }
        return new ReportWorkflowStepResponse(
                code,
                title,
                textOrDefault(rawStep.get("description"), ""),
                sanitizeStepType(rawStep.get("stepType")),
                "ON_NAVIGATE",
                fields(rawStep.get("fields")));
    }

    private List<ReportWorkflowFieldResponse> fields(Object value) {
        if (!(value instanceof List<?> rawFields)) {
            return List.of();
        }
        return rawFields.stream()
                .filter(Map.class::isInstance)
                .map(rawField -> parseField(castMap(rawField)))
                .filter(Objects::nonNull)
                .toList();
    }

    private ReportWorkflowFieldResponse parseField(Map<String, Object> rawField) {
        var key = text(rawField.get("key"));
        var label = text(rawField.get("label"));
        if (key == null || label == null) {
            return null;
        }
        return new ReportWorkflowFieldResponse(
                key,
                label,
                sanitizeFieldType(rawField.get("type")),
                text(rawField.get("placeholder")),
                booleanValue(rawField.get("required")));
    }

    private String sanitizeStepType(Object value) {
        var normalized = normalizeCode(value);
        if ("CHECKLIST".equals(normalized)
                || "PHOTO".equals(normalized)
                || "DAILY_SUPERVISION_ITEMS".equals(normalized)
                || "CHECKLIST_SOURCE".equals(normalized)) {
            return normalized;
        }
        return "FORM";
    }

    private String sanitizeFieldType(Object value) {
        var normalized = normalizeCode(value);
        if ("DATE".equals(normalized) || "NUMBER".equals(normalized) || "TEXTAREA".equals(normalized)) {
            return normalized.toLowerCase(Locale.ROOT);
        }
        return "text";
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value instanceof String text && Boolean.parseBoolean(text);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private String textOrDefault(Object value, String defaultValue) {
        var text = text(value);
        return text == null ? defaultValue : text;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        var text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String normalizeCode(Object value) {
        var text = text(value);
        return text == null ? null : text.toUpperCase(Locale.ROOT);
    }
}
