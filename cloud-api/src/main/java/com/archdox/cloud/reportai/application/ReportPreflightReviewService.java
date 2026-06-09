package com.archdox.cloud.reportai.application;

import com.archdox.cloud.documentai.application.DocumentAiReviewProperties;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.application.OfficePermissionService;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewRun;
import com.archdox.cloud.reportai.domain.ReportPreflightFindingResolutionStatus;
import com.archdox.cloud.reportai.dto.ReportPreflightLegalReferenceResponse;
import com.archdox.cloud.reportai.dto.ReportPreflightReviewFindingResponse;
import com.archdox.cloud.reportai.dto.ReportPreflightReviewRunResponse;
import com.archdox.cloud.reportai.dto.ResolveReportPreflightFindingRequest;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewFlowFactory;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewRequest;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewFindingRepository;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportPreflightReviewService {
    private final InspectionReportRepository reportRepository;
    private final OfficePermissionService permissionService;
    private final ReportPreflightReviewRunRepository runRepository;
    private final ReportPreflightReviewFindingRepository findingRepository;
    private final ReportPreflightReviewFlowFactory flowFactory;
    private final DocumentAiReviewProperties aiReviewProperties;
    private final OperationEventService operationEventService;

    public ReportPreflightReviewService(
            InspectionReportRepository reportRepository,
            OfficePermissionService permissionService,
            ReportPreflightReviewRunRepository runRepository,
            ReportPreflightReviewFindingRepository findingRepository,
            ReportPreflightReviewFlowFactory flowFactory,
            DocumentAiReviewProperties aiReviewProperties,
            OperationEventService operationEventService
    ) {
        this.reportRepository = reportRepository;
        this.permissionService = permissionService;
        this.runRepository = runRepository;
        this.findingRepository = findingRepository;
        this.flowFactory = flowFactory;
        this.aiReviewProperties = aiReviewProperties;
        this.operationEventService = operationEventService;
    }

    @Transactional
    public ReportPreflightReviewSubmission requestReview(Long reportId, UserPrincipal principal) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var report = reportRepository.findByIdAndOfficeId(reportId, officeId)
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
        permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        var run = runRepository.saveAndFlush(new ReportPreflightReviewRun(
                officeId,
                report.id(),
                report.contentRevision(),
                principal.userId(),
                OffsetDateTime.now()));
        operationEventService.record(
                officeId,
                OperationEventSeverity.INFO,
                "REPORT_PREFLIGHT_REVIEW_REQUESTED",
                "report-preflight-review",
                "report:" + report.id() + ":preflight-run:" + run.id(),
                "REPORT_PREFLIGHT_REVIEW_RUN",
                run.id(),
                principal.userId(),
                null,
                "Report preflight review requested.",
                requestPayload(report, run, aiReviewProperties.isEnabled()));
        var request = new ReportPreflightReviewRequest(officeId, report.id(), run.id(), principal.userId());
        return new ReportPreflightReviewSubmission(toResponse(run), flowFactory.create(request));
    }

    @Transactional(readOnly = true)
    public List<ReportPreflightReviewRunResponse> listRuns(Long reportId, UserPrincipal principal) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var report = requireReportWithWriteAccess(reportId, officeId, principal);
        return runRepository.findByOfficeIdAndReportIdOrderByRequestedAtDesc(officeId, report.id()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportPreflightReviewFindingResponse> listFindings(Long reportId, Long runId, UserPrincipal principal) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var report = requireReportWithWriteAccess(reportId, officeId, principal);
        runRepository.findByIdAndOfficeIdAndReportId(runId, officeId, report.id())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
        return findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(officeId, runId).stream()
                .map(this::toFindingResponse)
                .toList();
    }

    @Transactional
    public ReportPreflightReviewFindingResponse resolveFinding(
            Long reportId,
            Long runId,
            Long findingId,
            ResolveReportPreflightFindingRequest request,
            UserPrincipal principal
    ) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var report = requireReportWithWriteAccess(reportId, officeId, principal);
        var run = runRepository.findByIdAndOfficeIdAndReportId(runId, officeId, report.id())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
        var finding = findingRepository.findByIdAndOfficeIdAndReviewRunIdAndReportId(findingId, officeId, run.id(), report.id())
                .orElseThrow(() -> new NotFoundException("Report preflight review finding not found"));
        finding.resolve(resolutionStatus(request), request == null ? null : request.resolutionNote(), principal.userId(), OffsetDateTime.now());
        recomputeRunAfterResolution(run);
        operationEventService.record(
                officeId,
                OperationEventSeverity.INFO,
                "REPORT_PREFLIGHT_FINDING_RESOLVED",
                "report-preflight-review",
                "report:" + report.id() + ":preflight-run:" + run.id(),
                "REPORT_PREFLIGHT_REVIEW_FINDING",
                finding.id(),
                principal.userId(),
                null,
                "Report preflight finding resolution updated.",
                Map.of(
                        "reportId", report.id(),
                        "reviewRunId", run.id(),
                        "findingId", finding.id(),
                        "resolutionStatus", finding.resolutionStatus().name()));
        return toFindingResponse(finding);
    }

    private com.archdox.cloud.inspection.domain.InspectionReport requireReportWithWriteAccess(
            Long reportId,
            Long officeId,
            UserPrincipal principal
    ) {
        var report = reportRepository.findByIdAndOfficeId(reportId, officeId)
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
        permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        return report;
    }

    private ReportPreflightReviewRunResponse toResponse(ReportPreflightReviewRun run) {
        return new ReportPreflightReviewRunResponse(
                run.id(),
                run.officeId(),
                run.reportId(),
                run.reportRevision(),
                run.status().name(),
                run.requestedBy(),
                run.terminalReason(),
                run.hasHarness(),
                run.harnessRunId(),
                run.harnessStatus(),
                run.harnessAttempt(),
                run.harnessTerminalReason(),
                run.aiProviderCode(),
                run.aiModelId(),
                run.requestedAt(),
                run.updatedAt(),
                run.completedAt());
    }

    private ReportPreflightReviewFindingResponse toFindingResponse(ReportPreflightReviewFinding finding) {
        return new ReportPreflightReviewFindingResponse(
                finding.id(),
                finding.source(),
                finding.code(),
                finding.severity(),
                finding.location(),
                finding.message(),
                finding.evidence(),
                finding.attributesJson(),
                finding.attributesJson().get("engineRunId"),
                finding.attributesJson().get("engineStatus"),
                csvList(finding.attributesJson().get("legalReferences")),
                legalReferenceDetails(finding.attributesJson().get("legalReferenceDetails")),
                csvList(finding.attributesJson().get("engine.nextActions")),
                finding.resolutionStatus().name(),
                finding.resolutionNote(),
                finding.resolvedBy(),
                finding.resolvedAt(),
                finding.createdAt());
    }

    private void recomputeRunAfterResolution(ReportPreflightReviewRun run) {
        if (!"NEEDS_ATTENTION".equals(run.status().name())) {
            return;
        }
        var findings = findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(run.officeId(), run.id());
        var openAttention = findings.stream()
                .anyMatch(finding -> requiresResolutionForGeneration(finding)
                        && finding.resolutionStatus() == ReportPreflightFindingResolutionStatus.OPEN);
        if (!openAttention) {
            run.markPassed("PREFLIGHT_FINDINGS_RESOLVED", OffsetDateTime.now());
        }
    }

    private ReportPreflightFindingResolutionStatus resolutionStatus(ResolveReportPreflightFindingRequest request) {
        var value = request == null ? null : request.resolutionStatus();
        if (value == null || value.isBlank()) {
            return ReportPreflightFindingResolutionStatus.RESOLVED;
        }
        try {
            var status = ReportPreflightFindingResolutionStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (status == ReportPreflightFindingResolutionStatus.OPEN) {
                throw new BadRequestException("OPEN resolution status cannot be submitted");
            }
            return status;
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid preflight finding resolution status");
        }
    }

    private boolean isBlockingSeverity(String severity) {
        return "HIGH".equals(severity) || "CRITICAL".equals(severity);
    }

    private boolean requiresResolutionForGeneration(ReportPreflightReviewFinding finding) {
        if (isBlockingSeverity(finding.severity())) {
            return true;
        }
        if ("AI".equals(finding.source())) {
            return true;
        }
        return Boolean.parseBoolean(finding.attributesJson().getOrDefault("approvalRequired", "false"));
    }

    private static List<String> csvList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static List<ReportPreflightLegalReferenceResponse> legalReferenceDetails(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(ReportPreflightReviewService::legalReferenceDetail)
                .toList();
    }

    private static ReportPreflightLegalReferenceResponse legalReferenceDetail(String line) {
        var parts = line.split("\\t", -1);
        return new ReportPreflightLegalReferenceResponse(
                part(parts, 0),
                part(parts, 1),
                part(parts, 2),
                part(parts, 3),
                part(parts, 4),
                part(parts, 5),
                part(parts, 6),
                integer(part(parts, 7)),
                part(parts, 8));
    }

    private static String part(String[] parts, int index) {
        return index >= 0 && index < parts.length ? parts[index].trim() : "";
    }

    private static Integer integer(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, Object> requestPayload(
            InspectionReport report,
            ReportPreflightReviewRun run,
            boolean aiReviewPlanned
    ) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("reportId", report.id());
        payload.put("reviewRunId", run.id());
        payload.put("aiReviewPlanned", aiReviewPlanned);
        if (aiReviewPlanned) {
            payload.put("harnessRunId", run.harnessRunId());
            payload.put("aiProviderCode", run.aiProviderCode());
            payload.put("aiModelId", run.aiModelId());
        }
        return payload;
    }
}
