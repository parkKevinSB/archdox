package com.archdox.cloud.inspection.application;

import com.archdox.cloud.checklist.infra.InspectionChecklistAnswerRepository;
import com.archdox.cloud.configuration.application.ConfigurationRegistryService;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStep;
import com.archdox.cloud.inspection.dto.ReportSubmitValidationIssueResponse;
import com.archdox.cloud.inspection.dto.ReportSubmitValidationResponse;
import com.archdox.cloud.inspection.dto.ReportWorkflowDefinitionResponse;
import com.archdox.cloud.inspection.dto.ReportWorkflowFieldResponse;
import com.archdox.cloud.inspection.dto.ReportWorkflowStepResponse;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.photo.domain.PhotoAssetStatus;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportSubmitValidationService {
    private static final String CHECKLIST_SOURCE_STEP = "CHECKLIST_SOURCE";
    private static final String CHECKLIST_SELECTION_FIELD = "checklistSelection";

    private final InspectionReportStepRepository stepRepository;
    private final InspectionChecklistAnswerRepository checklistAnswerRepository;
    private final PhotoAssetRepository photoAssetRepository;
    private final ReportWorkflowDefinitionService workflowDefinitionService;
    private final ConfigurationRegistryService configurationRegistryService;

    public ReportSubmitValidationService(
            InspectionReportStepRepository stepRepository,
            InspectionChecklistAnswerRepository checklistAnswerRepository,
            PhotoAssetRepository photoAssetRepository,
            ReportWorkflowDefinitionService workflowDefinitionService,
            ConfigurationRegistryService configurationRegistryService
    ) {
        this.stepRepository = stepRepository;
        this.checklistAnswerRepository = checklistAnswerRepository;
        this.photoAssetRepository = photoAssetRepository;
        this.workflowDefinitionService = workflowDefinitionService;
        this.configurationRegistryService = configurationRegistryService;
    }

    @Transactional(readOnly = true)
    public ReportSubmitValidationResponse validate(InspectionReport report) {
        var blockingIssues = new ArrayList<ReportSubmitValidationIssueResponse>();
        var warnings = new ArrayList<ReportSubmitValidationIssueResponse>();
        var workflow = workflowDefinitionService.resolveForReport(report);
        var stepsByCode = stepRepository.findByReportIdOrderById(report.id()).stream()
                .collect(Collectors.toMap(
                        InspectionReportStep::stepCode,
                        step -> step,
                        (first, ignored) -> first));
        var ruleSet = configurationRegistryService
                .resolveForDocumentGeneration(report.officeId(), report.reportType())
                .ruleSet()
                .payload();
        var minWorkingPhotos = minWorkingPhotos(ruleSet, workflow);

        requireSiteForSiteBoundWorkflow(report, workflow, blockingIssues);
        for (var requiredStep : requiredSteps(ruleSet)) {
            requireSavedStep(stepsByCode, requiredStep, "Required step must be saved before submit.", blockingIssues);
        }
        for (var step : workflow.steps()) {
            validateWorkflowStep(report, step, stepsByCode, minWorkingPhotos, blockingIssues);
        }

        if (blockingIssues.isEmpty()) {
            return ReportSubmitValidationResponse.valid(warnings);
        }
        return ReportSubmitValidationResponse.invalid(blockingIssues, warnings);
    }

    private void requireSiteForSiteBoundWorkflow(
            InspectionReport report,
            ReportWorkflowDefinitionResponse workflow,
            ArrayList<ReportSubmitValidationIssueResponse> blockingIssues
    ) {
        var requiresSite = workflow.steps().stream()
                .anyMatch(step -> "DAILY_LOG".equals(normalizeCode(step.code())));
        if (!requiresSite || report.siteId() != null) {
            return;
        }
        blockingIssues.add(new ReportSubmitValidationIssueResponse(
                "REPORT_SITE_REQUIRED",
                "Site must be selected before submitting a construction supervision report.",
                "INSPECTION_REPORT",
                "siteId"));
    }

    private void validateWorkflowStep(
            InspectionReport report,
            ReportWorkflowStepResponse step,
            Map<String, InspectionReportStep> stepsByCode,
            int minWorkingPhotos,
            ArrayList<ReportSubmitValidationIssueResponse> blockingIssues
    ) {
        if ("CHECKLIST".equals(step.stepType())) {
            requireChecklistSaved(report, step.code(), blockingIssues);
            return;
        }
        if ("PHOTO".equals(step.stepType())) {
            requireWorkingPhotos(report, minWorkingPhotos, blockingIssues);
            return;
        }
        requireRequiredFields(step, stepsByCode, blockingIssues);
    }

    private void requireRequiredFields(
            ReportWorkflowStepResponse step,
            Map<String, InspectionReportStep> stepsByCode,
            ArrayList<ReportSubmitValidationIssueResponse> blockingIssues
    ) {
        var requiredFields = step.fields().stream()
                .filter(ReportWorkflowFieldResponse::required)
                .toList();
        if (requiredFields.isEmpty()) {
            return;
        }
        var savedStep = stepsByCode.get(step.code());
        if (savedStep == null) {
            if (isDefaultChecklistSourceStep(step)) {
                return;
            }
            blockingIssues.add(new ReportSubmitValidationIssueResponse(
                    "MISSING_STEP_" + step.code(),
                    step.title() + " step must be saved before submit.",
                    "INSPECTION_REPORT_STEP",
                    step.code()));
            return;
        }
        for (var field : requiredFields) {
            var value = savedStep.payloadJson() == null ? null : savedStep.payloadJson().get(field.key());
            if (isEmptyValue(value)) {
                if (isDefaultChecklistSourceField(step, field)) {
                    continue;
                }
                blockingIssues.add(new ReportSubmitValidationIssueResponse(
                        "MISSING_REQUIRED_FIELD_" + step.code() + "_" + field.key().toUpperCase(Locale.ROOT),
                        field.label() + " is required before submit.",
                        "INSPECTION_REPORT_FIELD",
                        step.code() + "." + field.key()));
            }
        }
    }

    private void requireChecklistSaved(
            InspectionReport report,
            String stepCode,
            ArrayList<ReportSubmitValidationIssueResponse> blockingIssues
    ) {
        var hasStep = stepRepository.existsByReportIdAndStepCode(report.id(), stepCode);
        var hasAnswer = checklistAnswerRepository.existsByOfficeIdAndReportId(report.officeId(), report.id());
        if (!hasStep && !hasAnswer) {
            blockingIssues.add(new ReportSubmitValidationIssueResponse(
                    "MISSING_STEP_" + stepCode,
                    "Checklist step must be saved before submit.",
                    "INSPECTION_REPORT_STEP",
                    stepCode));
        }
    }

    @Transactional(readOnly = true)
    public void requireValid(InspectionReport report) {
        var validation = validate(report);
        if (!validation.valid()) {
            throw new ReportSubmitValidationException(validation);
        }
    }

    private void requireSavedStep(
            Map<String, InspectionReportStep> stepsByCode,
            String stepCode,
            String message,
            ArrayList<ReportSubmitValidationIssueResponse> blockingIssues
    ) {
        if (isChecklistSourceStep(stepCode)) {
            return;
        }
        if (!stepsByCode.containsKey(stepCode)) {
            blockingIssues.add(new ReportSubmitValidationIssueResponse(
                    "MISSING_STEP_" + stepCode,
                    message,
                    "INSPECTION_REPORT_STEP",
                    stepCode));
        }
    }

    private void requireWorkingPhotos(
            InspectionReport report,
            int minWorkingPhotos,
            ArrayList<ReportSubmitValidationIssueResponse> blockingIssues
    ) {
        if (minWorkingPhotos <= 0) {
            return;
        }
        var uploadedCount = photoAssetRepository.countUploadedAsset(
                report.id(),
                report.officeId(),
                PhotoAssetType.WORKING,
                PhotoAssetStatus.UPLOADED);
        if (uploadedCount < minWorkingPhotos) {
            blockingIssues.add(new ReportSubmitValidationIssueResponse(
                    "MISSING_WORKING_PHOTO",
                    "At least " + minWorkingPhotos + " uploaded working photo(s) are required before submit.",
                    "PHOTO",
                    "WORKING"));
        }
    }

    private int minWorkingPhotos(Map<String, Object> ruleSet, ReportWorkflowDefinitionResponse workflow) {
        var configured = integerValue(ruleSet.get("minWorkingPhotos"));
        if (configured == null) {
            configured = integerValue(ruleSet.get("minPhotos"));
        }
        if (configured != null) {
            return Math.max(0, configured);
        }
        return workflow.steps().stream().anyMatch(step -> "PHOTO".equals(step.stepType())) ? 1 : 0;
    }

    @SuppressWarnings("unchecked")
    private List<String> requiredSteps(Map<String, Object> ruleSet) {
        var rawRequiredSteps = ruleSet.get("requiredSteps");
        if (!(rawRequiredSteps instanceof List<?> steps)) {
            return List.of();
        }
        return steps.stream()
                .map(this::normalizeCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    private boolean isDefaultChecklistSourceStep(ReportWorkflowStepResponse step) {
        return isChecklistSourceStep(step.code());
    }

    private boolean isDefaultChecklistSourceField(ReportWorkflowStepResponse step, ReportWorkflowFieldResponse field) {
        return isChecklistSourceStep(step.code()) && CHECKLIST_SELECTION_FIELD.equals(field.key());
    }

    private boolean isChecklistSourceStep(String stepCode) {
        return CHECKLIST_SOURCE_STEP.equals(normalizeCode(stepCode));
    }

    private String normalizeCode(Object value) {
        if (value == null) {
            return null;
        }
        var text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text.toUpperCase(Locale.ROOT);
    }
}
