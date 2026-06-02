package com.archdox.cloud.inspection.application;

import com.archdox.cloud.checklist.domain.ChecklistSchema;
import com.archdox.cloud.checklist.domain.ChecklistSchemaStatus;
import com.archdox.cloud.checklist.infra.ChecklistSchemaRepository;
import com.archdox.cloud.configuration.application.ConfigurationRegistryService;
import com.archdox.cloud.configuration.application.ResolvedDocumentConfigPart;
import com.archdox.cloud.configuration.domain.ConfigResolutionSource;
import com.archdox.cloud.documenttype.application.DocumentTypeRegistryService;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.dto.ReportWorkflowDefinitionResponse;
import com.archdox.cloud.inspection.dto.ReportWorkflowFieldResponse;
import com.archdox.cloud.inspection.dto.ReportWorkflowStepResponse;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.inspectiontarget.domain.InspectionReportTarget;
import com.archdox.cloud.inspectiontarget.domain.InspectionReportTargetRole;
import com.archdox.cloud.inspectiontarget.infra.InspectionReportTargetRepository;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.site.infra.SiteRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportWorkflowDefinitionService {
    private static final String BUILT_IN_DEFAULT_SOURCE = "BUILT_IN_DEFAULT";
    private static final String DOCUMENT_TYPE_DEFAULT_SOURCE = "DOCUMENT_TYPE_DEFAULT";

    private final InspectionReportRepository reportRepository;
    private final ConfigurationRegistryService configurationRegistryService;
    private final SiteRepository siteRepository;
    private final InspectionReportTargetRepository reportTargetRepository;
    private final ChecklistSchemaRepository checklistSchemaRepository;
    private final DocumentTypeRegistryService documentTypeRegistryService;

    public ReportWorkflowDefinitionService(
            InspectionReportRepository reportRepository,
            ConfigurationRegistryService configurationRegistryService,
            SiteRepository siteRepository,
            InspectionReportTargetRepository reportTargetRepository,
            ChecklistSchemaRepository checklistSchemaRepository,
            DocumentTypeRegistryService documentTypeRegistryService
    ) {
        this.reportRepository = reportRepository;
        this.configurationRegistryService = configurationRegistryService;
        this.siteRepository = siteRepository;
        this.reportTargetRepository = reportTargetRepository;
        this.checklistSchemaRepository = checklistSchemaRepository;
        this.documentTypeRegistryService = documentTypeRegistryService;
    }

    @Transactional(readOnly = true)
    public ReportWorkflowDefinitionResponse resolve(Long reportId) {
        return resolveForReport(requireReport(reportId));
    }

    @Transactional(readOnly = true)
    public ReportWorkflowDefinitionResponse resolveForReport(InspectionReport report) {
        var siteType = resolveSiteType(report);
        var targetType = resolveTargetType(report);
        var checklistSchema = resolveChecklistSchema(report, siteType, targetType);
        var workflowPart = configurationRegistryService
                .resolveForDocumentGeneration(report.officeId(), report.reportType())
                .workflow();

        var configuredSteps = parseConfiguredSteps(workflowPart.payload());
        if (!configuredSteps.isEmpty() && workflowPart.source() != ConfigResolutionSource.NOT_CONFIGURED) {
            return new ReportWorkflowDefinitionResponse(
                    report.id(),
                    report.officeId(),
                    report.reportType(),
                    siteType,
                    targetType,
                    textOrDefault(workflowPart.payload().get("flowId"), workflowPart.code()),
                    textOrDefault(workflowPart.payload().get("title"), workflowPart.name()),
                    workflowPart.source().name(),
                    workflowPart.definitionId(),
                    workflowPart.revisionId(),
                    workflowPart.version(),
                    checklistSchema == null ? null : checklistSchema.id(),
                    checklistSchema == null ? null : checklistSchema.code(),
                    checklistSchema == null ? null : checklistSchema.version(),
                    configuredSteps);
        }

        return builtInDefault(report, siteType, targetType, checklistSchema);
    }

    private InspectionReport requireReport(Long reportId) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        return reportRepository.findByIdAndOfficeId(reportId, officeId)
                .orElseThrow(() -> new NotFoundException(
                        "REPORT_NOT_FOUND",
                        "errors.report.notFound",
                        "Inspection report not found",
                        Map.of("reportId", reportId)));
    }

    private String resolveSiteType(InspectionReport report) {
        if (report.siteId() == null) {
            return null;
        }
        return siteRepository.findByIdAndOfficeId(report.siteId(), report.officeId())
                .map(site -> normalizeCode(site.siteType()))
                .orElse(null);
    }

    private String resolveTargetType(InspectionReport report) {
        var targets = reportTargetRepository.findByOfficeIdAndReportIdOrderByRoleAscIdAsc(
                report.officeId(),
                report.id());
        return targets.stream()
                .filter(target -> target.role() == InspectionReportTargetRole.PRIMARY)
                .map(this::snapshotTargetType)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> targets.stream()
                        .map(this::snapshotTargetType)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null));
    }

    private String snapshotTargetType(InspectionReportTarget target) {
        return normalizeCode(target.snapshotJson().get("targetType"));
    }

    private ChecklistSchema resolveChecklistSchema(InspectionReport report, String siteType, String targetType) {
        return checklistSchemaRepository.findResolutionCandidates(
                        report.officeId(),
                        normalizeCode(report.reportType()),
                        siteType,
                        targetType,
                        ChecklistSchemaStatus.ACTIVE,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private ReportWorkflowDefinitionResponse builtInDefault(
            InspectionReport report,
            String siteType,
            String targetType,
            ChecklistSchema checklistSchema
    ) {
        var documentType = documentTypeRegistryService.resolveByReportType(report.officeId(), report.reportType());
        if (documentType.isPresent()) {
            var definition = documentType.get();
            var steps = documentTypeRegistryService.workflowSteps(definition);
            if (!steps.isEmpty()) {
                return new ReportWorkflowDefinitionResponse(
                        report.id(),
                        report.officeId(),
                        report.reportType(),
                        siteType,
                        targetType,
                        documentTypeRegistryService.workflowId(definition),
                        documentTypeRegistryService.workflowTitle(definition),
                        DOCUMENT_TYPE_DEFAULT_SOURCE,
                        definition.id(),
                        null,
                        null,
                        checklistSchema == null ? null : checklistSchema.id(),
                        checklistSchema == null ? null : checklistSchema.code(),
                        checklistSchema == null ? null : checklistSchema.version(),
                        steps);
            }
        }
        return new ReportWorkflowDefinitionResponse(
                report.id(),
                report.officeId(),
                report.reportType(),
                siteType,
                targetType,
                "inspection-report-writing",
                "리포트 작성",
                BUILT_IN_DEFAULT_SOURCE,
                null,
                null,
                null,
                checklistSchema == null ? null : checklistSchema.id(),
                checklistSchema == null ? null : checklistSchema.code(),
                checklistSchema == null ? null : checklistSchema.version(),
                builtInSteps());
    }

    private List<ReportWorkflowStepResponse> builtInSteps() {
        return List.of(
                step("BASIC_INFO", "기본 정보", "일자, 날씨, 담당자처럼 보고서가 공유하는 머리말 정보를 정리합니다.", "FORM",
                        List.of(
                                field("inspectionDate", "점검일", "date", null, true),
                                field("weather", "날씨", "text", "맑음", false),
                                field("inspectorName", "담당자", "text", "홍길동", true))),
                step("WORK_SUMMARY", "작업 요약", "현장에서 확인한 작업 내용과 참여 인원을 기록합니다.", "FORM",
                        List.of(
                                field("workSummary", "작업 내용", "textarea", "주요 작업과 확인 내용을 입력하세요.", false),
                                field("workerCount", "작업 인원", "number", "0", false))),
                step("CHECKLIST", "점검 결과", "체크리스트 요약과 이슈 수를 정리합니다. 상세 체크리스트는 아래에서 관리합니다.", "CHECKLIST",
                        List.of(
                                field("checklistSummary", "점검 요약", "textarea", "적합/미흡 항목을 요약하세요.", false),
                                field("issueCount", "이슈 수", "number", "0", false))),
                step("PHOTOS", "현장 사진", "보고서에 포함할 사진과 작업본 준비 상태를 확인합니다.", "PHOTO", List.of()),
                step("REMARKS", "비고/조치", "특이사항, 다음 조치, 전달 메모를 정리합니다.", "FORM",
                        List.of(
                                field("remarks", "비고", "textarea", "특이사항이 없으면 비워둘 수 있습니다.", false),
                                field("nextAction", "다음 조치", "text", "보완 요청, 재점검 예정 등", false))));
    }

    private ReportWorkflowStepResponse step(
            String code,
            String title,
            String description,
            String stepType,
            List<ReportWorkflowFieldResponse> fields
    ) {
        return new ReportWorkflowStepResponse(code, title, description, stepType, "ON_NAVIGATE", fields);
    }

    private ReportWorkflowFieldResponse field(
            String key,
            String label,
            String type,
            String placeholder,
            boolean required
    ) {
        return new ReportWorkflowFieldResponse(key, label, type, placeholder, required);
    }

    @SuppressWarnings("unchecked")
    private List<ReportWorkflowStepResponse> parseConfiguredSteps(Map<String, Object> payload) {
        var rawSteps = payload.get("steps");
        if (!(rawSteps instanceof List<?> steps)) {
            return List.of();
        }
        return steps.stream()
                .filter(Map.class::isInstance)
                .map(rawStep -> parseConfiguredStep((Map<String, Object>) rawStep))
                .filter(Objects::nonNull)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private ReportWorkflowStepResponse parseConfiguredStep(Map<String, Object> rawStep) {
        var code = normalizeCode(rawStep.get("code"));
        var title = text(rawStep.get("title"));
        if (code == null || title == null) {
            return null;
        }
        var rawFields = rawStep.get("fields");
        var fields = rawFields instanceof List<?> list
                ? list.stream()
                        .filter(Map.class::isInstance)
                        .map(rawField -> parseConfiguredField((Map<String, Object>) rawField))
                        .filter(Objects::nonNull)
                        .toList()
                : List.<ReportWorkflowFieldResponse>of();
        return new ReportWorkflowStepResponse(
                code,
                title,
                textOrDefault(rawStep.get("description"), ""),
                sanitizeStepType(rawStep.get("stepType")),
                "ON_NAVIGATE",
                fields);
    }

    private ReportWorkflowFieldResponse parseConfiguredField(Map<String, Object> rawField) {
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
                || "DAILY_SUPERVISION_ITEMS".equals(normalized)) {
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
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value instanceof String textValue && Boolean.parseBoolean(textValue);
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
