package com.archdox.cloud.inspection.application;

import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.documenttype.application.DocumentTypeRegistryService;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStatus;
import com.archdox.cloud.inspection.domain.InspectionReportStep;
import com.archdox.cloud.inspection.domain.PayloadStorageMode;
import com.archdox.cloud.inspection.dto.CreateInspectionReportRequest;
import com.archdox.cloud.inspection.dto.InspectionReportResponse;
import com.archdox.cloud.inspection.dto.InspectionStepResponse;
import com.archdox.cloud.inspection.dto.ReportSubmitValidationResponse;
import com.archdox.cloud.inspection.dto.SaveInspectionStepRequest;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.application.OfficePermissionService;
import com.archdox.cloud.project.application.ProjectService;
import com.archdox.cloud.site.application.SiteService;
import com.archdox.cloud.supervisionledger.application.SiteSupervisionLedgerService;
import com.archdox.cloud.workspace.application.WorkspaceCascadeDeletionService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionReportService {
    private final InspectionReportRepository reportRepository;
    private final InspectionReportStepRepository stepRepository;
    private final ProjectService projectService;
    private final SiteService siteService;
    private final OfficePermissionService permissionService;
    private final ReportSubmitValidationService submitValidationService;
    private final DocumentTypeRegistryService documentTypeRegistryService;
    private final WorkspaceCascadeDeletionService deletionService;
    private final SiteSupervisionLedgerService supervisionLedgerService;

    public InspectionReportService(
            InspectionReportRepository reportRepository,
            InspectionReportStepRepository stepRepository,
            ProjectService projectService,
            SiteService siteService,
            OfficePermissionService permissionService,
            ReportSubmitValidationService submitValidationService,
            DocumentTypeRegistryService documentTypeRegistryService,
            WorkspaceCascadeDeletionService deletionService,
            SiteSupervisionLedgerService supervisionLedgerService
    ) {
        this.reportRepository = reportRepository;
        this.stepRepository = stepRepository;
        this.projectService = projectService;
        this.siteService = siteService;
        this.permissionService = permissionService;
        this.submitValidationService = submitValidationService;
        this.documentTypeRegistryService = documentTypeRegistryService;
        this.deletionService = deletionService;
        this.supervisionLedgerService = supervisionLedgerService;
    }

    @Transactional(readOnly = true)
    public List<InspectionReportResponse> list(Long projectId, InspectionReportStatus status, UserPrincipal principal) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var reports = projectId == null
                ? reportRepository.findByOfficeIdOrderByUpdatedAtDesc(officeId)
                : reportRepository.findByOfficeIdAndProjectIdOrderByUpdatedAtDesc(officeId, projectId);
        return reports.stream()
                .filter(report -> status == null || report.status() == status)
                .map(report -> toResponse(report, principal))
                .toList();
    }

    @Transactional
    public InspectionReportResponse create(CreateInspectionReportRequest request, UserPrincipal principal) {
        projectService.requireProject(request.projectId());
        if (request.siteId() != null) {
            siteService.requireSiteForProject(request.siteId(), request.projectId());
        }
        var officeId = OfficeContext.requireCurrentOfficeId();
        permissionService.requireReportWriter(principal.userId(), officeId, request.projectId(), null);
        var documentType = documentTypeRegistryService.requireForReportCreation(officeId, request.reportType());
        var now = OffsetDateTime.now();
        var report = new InspectionReport(
                officeId,
                request.projectId(),
                request.siteId(),
                generateReportNo(now),
                documentType.reportType(),
                defaultTitle(request.title(), documentType.name()),
                request.templateId(),
                principal.userId(),
                now);
        return toResponse(reportRepository.save(report), principal);
    }

    @Transactional(readOnly = true)
    public InspectionReportResponse get(Long reportId, UserPrincipal principal) {
        return toResponse(requireReport(reportId), principal);
    }

    @Transactional(readOnly = true)
    public List<InspectionStepResponse> listSteps(Long reportId) {
        var report = requireReport(reportId);
        return stepRepository.findByReportIdOrderById(report.id()).stream()
                .map(this::toStepResponse)
                .toList();
    }

    @Transactional
    public InspectionStepResponse saveStep(Long reportId, String stepCode, SaveInspectionStepRequest request, UserPrincipal principal) {
        var report = requireReport(reportId);
        permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        requireCanSaveStep(report);
        var now = OffsetDateTime.now();
        var normalizedStepCode = stepCode.trim().toUpperCase();
        var step = stepRepository.findByReportIdAndStepCode(report.id(), normalizedStepCode)
                .map(existing -> {
                    existing.update(request.payload(), principal.userId(), now);
                    return existing;
                })
                .orElseGet(() -> stepRepository.save(new InspectionReportStep(
                        report,
                        normalizedStepCode,
                        PayloadStorageMode.CLOUD_ENCRYPTED,
                        request.payload(),
                        principal.userId(),
                        now)));
        report.markStepSaved(normalizedStepCode, now);
        if ("DAILY_LOG".equals(normalizedStepCode) || "BASIC_INFO".equals(normalizedStepCode)) {
            supervisionLedgerService.syncReportDailyLog(report, principal.userId(), now);
        }
        return toStepResponse(step);
    }

    @Transactional
    public InspectionStepResponse applyPreflightFixStep(Long reportId, String stepCode, SaveInspectionStepRequest request, UserPrincipal principal) {
        var report = requireReport(reportId);
        permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        requireCanApplyPreflightFixStep(report);
        var now = OffsetDateTime.now();
        var normalizedStepCode = stepCode.trim().toUpperCase();
        var step = stepRepository.findByReportIdAndStepCode(report.id(), normalizedStepCode)
                .map(existing -> {
                    existing.update(request.payload(), principal.userId(), now);
                    return existing;
                })
                .orElseGet(() -> stepRepository.save(new InspectionReportStep(
                        report,
                        normalizedStepCode,
                        PayloadStorageMode.CLOUD_ENCRYPTED,
                        request.payload(),
                        principal.userId(),
                        now)));
        if (report.canSaveStep()) {
            report.markStepSaved(normalizedStepCode, now);
        } else {
            report.markSubmittedPreflightFixApplied(normalizedStepCode, now);
        }
        if ("DAILY_LOG".equals(normalizedStepCode) || "BASIC_INFO".equals(normalizedStepCode)) {
            supervisionLedgerService.syncReportDailyLog(report, principal.userId(), now);
        }
        return toStepResponse(step);
    }

    @Transactional
    public InspectionReportResponse submit(Long reportId, UserPrincipal principal) {
        var report = requireReport(reportId);
        permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        requireCanSubmit(report);
        submitValidationService.requireValid(report);
        var now = OffsetDateTime.now();
        report.submit(now);
        supervisionLedgerService.confirmReportRevision(report, principal.userId(), now);
        return toResponse(report, principal);
    }

    @Transactional
    public InspectionReportResponse reopenForEdit(Long reportId, UserPrincipal principal) {
        var report = requireReport(reportId);
        permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        requireCanReopenForEdit(report);
        report.reopenForEdit(OffsetDateTime.now());
        return toResponse(report, principal);
    }

    @Transactional(readOnly = true)
    public ReportSubmitValidationResponse validateSubmit(Long reportId, UserPrincipal principal) {
        var report = requireReport(reportId);
        permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        return submitValidationService.validate(report);
    }

    @Transactional
    public InspectionReportResponse cancel(Long reportId, UserPrincipal principal) {
        var report = requireReport(reportId);
        permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        requireCanCancel(report);
        report.cancel(OffsetDateTime.now());
        return toResponse(report, principal);
    }

    @Transactional
    public void delete(Long reportId, UserPrincipal principal) {
        var report = requireReport(reportId);
        permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        deletionService.deleteReport(report.officeId(), report.id());
    }

    @Transactional
    public void removePhotoReference(Long reportId, Long photoId, Long userId, OffsetDateTime now) {
        var report = requireReport(reportId);
        stepRepository.findByReportIdAndStepCode(report.id(), "DAILY_LOG")
                .ifPresent(step -> {
                    var removal = removePhotoIdFromDailyLogPayload(step.payloadJson(), photoId);
                    if (removal.changed()) {
                        step.update(removal.payload(), userId, now);
                        supervisionLedgerService.syncReportDailyLog(report, userId, now);
                    }
                });
    }

    public InspectionReport requireReport(Long reportId) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        return reportRepository.findByIdAndOfficeId(reportId, officeId)
                .orElseThrow(() -> new NotFoundException(
                        "REPORT_NOT_FOUND",
                        "errors.report.notFound",
                        "Inspection report not found",
                        Map.of("reportId", reportId)));
    }

    private void requireCanSaveStep(InspectionReport report) {
        if (!report.canSaveStep()) {
            throw new BadRequestException(
                    "REPORT_STEP_SAVE_NOT_ALLOWED",
                    "errors.report.stepSaveNotAllowed",
                    "Inspection report cannot be edited in status " + report.status(),
                    reportParams(report));
        }
    }

    private void requireCanApplyPreflightFixStep(InspectionReport report) {
        if (!report.canSaveStep() && !report.canApplyPreflightFixToSubmittedRevision()) {
            throw new BadRequestException(
                    "REPORT_PREFLIGHT_FIX_STEP_SAVE_NOT_ALLOWED",
                    "errors.report.stepSaveNotAllowed",
                    "Inspection report cannot be changed by a preflight fix in status " + report.status(),
                    reportParams(report));
        }
    }

    private void requireCanSubmit(InspectionReport report) {
        if (!report.canSubmit()) {
            throw new BadRequestException(
                    "REPORT_SUBMIT_NOT_ALLOWED",
                    "errors.report.submitNotAllowed",
                    "Inspection report cannot be submitted in status " + report.status(),
                    reportParams(report));
        }
    }

    private void requireCanReopenForEdit(InspectionReport report) {
        if (!report.canReopenForEdit()) {
            throw new BadRequestException(
                    "REPORT_REOPEN_NOT_ALLOWED",
                    "errors.report.reopenNotAllowed",
                    "Inspection report cannot be reopened for edit in status " + report.status(),
                    reportParams(report));
        }
    }

    private void requireCanCancel(InspectionReport report) {
        if (!report.canCancel()) {
            throw new BadRequestException(
                    "REPORT_CANCEL_NOT_ALLOWED",
                    "errors.report.cancelNotAllowed",
                    "Inspection report cannot be cancelled in status " + report.status(),
                    reportParams(report));
        }
    }

    private Map<String, Object> reportParams(InspectionReport report) {
        return Map.of(
                "reportId", report.id(),
                "status", report.status().name(),
                "contentRevision", report.contentRevision());
    }

    private InspectionReportResponse toResponse(InspectionReport report, UserPrincipal principal) {
        var writeAllowed = permissionService.canWriteReport(
                principal.userId(),
                report.officeId(),
                report.projectId(),
                report.id());
        return new InspectionReportResponse(
                report.id(),
                report.officeId(),
                report.projectId(),
                report.siteId(),
                report.reportNo(),
                report.reportType(),
                report.title(),
                report.status(),
                report.currentStep(),
                report.templateId(),
                report.contentRevision(),
                report.submittedRevision(),
                report.generatedRevision(),
                report.lastDocumentJobId(),
                writeAllowed,
                writeAllowed && report.canReopenForEdit());
    }

    private InspectionStepResponse toStepResponse(InspectionReportStep step) {
        return new InspectionStepResponse(
                step.stepCode(),
                step.payloadStorageMode(),
                step.payloadJson(),
                step.clientRevision(),
                step.savedAt());
    }

    private String generateReportNo(OffsetDateTime now) {
        return "RPT-" + DateTimeFormatter.BASIC_ISO_DATE.format(now) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String defaultTitle(String title, String fallback) {
        var trimmed = trimToNull(title);
        return trimmed == null ? fallback : trimmed;
    }

    private DailyLogPhotoRemoval removePhotoIdFromDailyLogPayload(Map<String, Object> payload, Long photoId) {
        if (payload == null || payload.isEmpty()) {
            return new DailyLogPhotoRemoval(Map.of(), false);
        }
        var nextPayload = new LinkedHashMap<>(payload);
        var dailyItems = mapValue(nextPayload.get("dailyItems"));
        if (dailyItems.isEmpty()) {
            return new DailyLogPhotoRemoval(nextPayload, false);
        }
        var nextDailyItems = new LinkedHashMap<>(dailyItems);
        var groups = listValue(nextDailyItems.get("groups"));
        var changed = false;
        var nextGroups = new ArrayList<Object>();
        for (Object groupValue : groups) {
            var group = mapValue(groupValue);
            if (group.isEmpty()) {
                nextGroups.add(groupValue);
                continue;
            }
            var nextGroup = new LinkedHashMap<>(group);
            var entries = listValue(nextGroup.get("entries"));
            var nextEntries = new ArrayList<Object>();
            for (Object entryValue : entries) {
                var entry = mapValue(entryValue);
                if (entry.isEmpty()) {
                    nextEntries.add(entryValue);
                    continue;
                }
                var nextEntry = new LinkedHashMap<>(entry);
                var checklistRows = listValue(nextEntry.get("checklistRows"));
                var nextChecklistRows = new ArrayList<Object>();
                for (Object rowValue : checklistRows) {
                    var row = mapValue(rowValue);
                    if (row.isEmpty()) {
                        nextChecklistRows.add(rowValue);
                        continue;
                    }
                    var nextRow = new LinkedHashMap<>(row);
                    var rowPhotoIds = listValue(nextRow.get("photoIds"));
                    var nextRowPhotoIds = new ArrayList<Object>();
                    for (Object rawPhotoId : rowPhotoIds) {
                        var currentPhotoId = longValue(rawPhotoId);
                        if (currentPhotoId != null && currentPhotoId.equals(photoId)) {
                            changed = true;
                            continue;
                        }
                        nextRowPhotoIds.add(rawPhotoId);
                    }
                    nextRow.put("photoIds", nextRowPhotoIds);
                    nextChecklistRows.add(nextRow);
                }
                if (!checklistRows.isEmpty()) {
                    nextEntry.put("checklistRows", nextChecklistRows);
                }
                nextEntries.add(nextEntry);
            }
            nextGroup.put("entries", nextEntries);
            nextGroups.add(nextGroup);
        }
        if (changed) {
            nextDailyItems.put("groups", nextGroups);
            nextPayload.put("dailyItems", nextDailyItems);
        }
        return new DailyLogPhotoRemoval(nextPayload, changed);
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        raw.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private record DailyLogPhotoRemoval(Map<String, Object> payload, boolean changed) {
    }
}
