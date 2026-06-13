package com.archdox.cloud.reportai.application;

import com.archdox.cloud.documentai.application.DocumentAiReviewProperties;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.application.InspectionReportService;
import com.archdox.cloud.inspection.dto.SaveInspectionStepRequest;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportPreflightReviewService {
    private final InspectionReportRepository reportRepository;
    private final InspectionReportStepRepository stepRepository;
    private final InspectionReportService inspectionReportService;
    private final OfficePermissionService permissionService;
    private final ReportPreflightReviewRunRepository runRepository;
    private final ReportPreflightReviewFindingRepository findingRepository;
    private final ReportPreflightReviewFlowFactory flowFactory;
    private final DocumentAiReviewProperties aiReviewProperties;
    private final OperationEventService operationEventService;

    public ReportPreflightReviewService(
            InspectionReportRepository reportRepository,
            InspectionReportStepRepository stepRepository,
            InspectionReportService inspectionReportService,
            OfficePermissionService permissionService,
            ReportPreflightReviewRunRepository runRepository,
            ReportPreflightReviewFindingRepository findingRepository,
            ReportPreflightReviewFlowFactory flowFactory,
            DocumentAiReviewProperties aiReviewProperties,
            OperationEventService operationEventService
    ) {
        this.reportRepository = reportRepository;
        this.stepRepository = stepRepository;
        this.inspectionReportService = inspectionReportService;
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

    @Transactional
    public ReportPreflightReviewFindingResponse applyFindingFix(
            Long reportId,
            Long runId,
            Long findingId,
            UserPrincipal principal
    ) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var report = requireReportWithWriteAccess(reportId, officeId, principal);
        var run = runRepository.findByIdAndOfficeIdAndReportId(runId, officeId, report.id())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
        if (run.reportRevision() != report.contentRevision()) {
            throw new BadRequestException(
                    "REPORT_PREFLIGHT_RUN_STALE",
                    "errors.reportPreflight.stale",
                    "Report preflight review run is stale. Run the review again before applying a fix.",
                    Map.of("reportId", report.id(), "reviewRunId", run.id()));
        }
        var finding = findingRepository.findByIdAndOfficeIdAndReviewRunIdAndReportId(findingId, officeId, run.id(), report.id())
                .orElseThrow(() -> new NotFoundException("Report preflight review finding not found"));
        if (finding.resolutionStatus() != ReportPreflightFindingResolutionStatus.OPEN) {
            throw new BadRequestException(
                    "REPORT_PREFLIGHT_FINDING_ALREADY_RESOLVED",
                    "errors.reportPreflight.findingAlreadyResolved",
                    "Report preflight review finding is already resolved.",
                    Map.of("findingId", finding.id()));
        }
        var target = fixTarget(finding);
        var replacement = fixReplacement(finding);
        if (target == null || replacement.isBlank()) {
            throw new BadRequestException(
                    "REPORT_PREFLIGHT_FIX_NOT_AVAILABLE",
                    "errors.reportPreflight.fixNotAvailable",
                    "This preflight finding does not have a safe automatic fix.",
                    Map.of("findingId", finding.id()));
        }
        var applyToSubmittedRevision = report.canApplyPreflightFixToSubmittedRevision();
        if (!report.canSaveStep() && !applyToSubmittedRevision) {
            if (!report.canReopenForEdit()) {
                throw new BadRequestException(
                        "REPORT_PREFLIGHT_FIX_NOT_EDITABLE",
                        "errors.reportPreflight.fixNotEditable",
                        "Report cannot be edited while applying this preflight fix.",
                        Map.of("reportId", report.id(), "status", report.status().name()));
            }
            report.reopenForEdit(OffsetDateTime.now());
        }
        var payload = stepRepository.findByReportIdAndStepCode(report.id(), target.stepCode())
                .map(step -> mutableMap(step.payloadJson() == null ? Map.<String, Object>of() : step.payloadJson()))
                .orElseGet(LinkedHashMap::new);
        applyReplacement(payload, target, replacement);
        var saveRequest = new SaveInspectionStepRequest(payload);
        if (applyToSubmittedRevision) {
            inspectionReportService.applyPreflightFixStep(report.id(), target.stepCode(), saveRequest, principal);
        } else {
            inspectionReportService.saveStep(report.id(), target.stepCode(), saveRequest, principal);
        }
        var eventPayload = fixAppliedEventPayload(report.id(), run.id(), finding.id(), target);
        finding.resolve(
                ReportPreflightFindingResolutionStatus.RESOLVED,
                "AI_FIX_APPLIED:" + target.description(),
                principal.userId(),
                OffsetDateTime.now());
        recomputeRunAfterResolution(run);
        operationEventService.record(
                officeId,
                OperationEventSeverity.INFO,
                "REPORT_PREFLIGHT_FINDING_FIX_APPLIED",
                "report-preflight-review",
                "report:" + report.id() + ":preflight-run:" + run.id(),
                "REPORT_PREFLIGHT_REVIEW_FINDING",
                finding.id(),
                principal.userId(),
                null,
                "Report preflight finding fix applied.",
                eventPayload);
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

    private boolean requiresResolutionForGeneration(ReportPreflightReviewFinding finding) {
        return ReportPreflightFindingClassifier.requiresResolutionForGeneration(finding);
    }

    private static FindingFixTarget fixTarget(ReportPreflightReviewFinding finding) {
        if (!fixSourceAllowed(finding)) {
            return null;
        }
        if (!"LOW".equals(finding.severity()) && !"MEDIUM".equals(finding.severity())) {
            return null;
        }
        if (!"WORDING".equals(finding.attributesJson().get("category"))) {
            var legalCategory = finding.attributesJson().get("category");
            if (!"LEGAL_REVIEW".equals(finding.source())
                    || (!"COMPLIANCE".equals(legalCategory)
                    && !"LEGAL_RISK".equals(legalCategory)
                    && !"EVIDENCE".equals(legalCategory))) {
                return null;
            }
        }
        var relatedFieldPath = textValue(finding.attributesJson().get("relatedFieldPath"));
        var location = !relatedFieldPath.isBlank()
                ? relatedFieldPath
                : finding.location() == null ? "" : finding.location().trim();
        if (location.endsWith("REMARKS.issueAndAction")
                || location.endsWith("REMARKS.payload.issueAndAction")
                || "REMARKS.issueAndAction".equals(location)) {
            return FindingFixTarget.remarks("issueAndAction");
        }
        if (location.endsWith("REMARKS.nextAction")
                || location.endsWith("REMARKS.payload.nextAction")
                || "REMARKS.nextAction".equals(location)) {
            return FindingFixTarget.remarks("nextAction");
        }
        var groupedDaily = groupedDailyLogLocation(location);
        if (groupedDaily != null) {
            return groupedDaily;
        }
        var flatDaily = flatDailyLogLocation(location);
        if (flatDaily != null) {
            return flatDaily;
        }
        return null;
    }

    private static boolean fixSourceAllowed(ReportPreflightReviewFinding finding) {
        if ("AI".equals(finding.source())) {
            return true;
        }
        if ("LEGAL_REVIEW".equals(finding.source())) {
            return true;
        }
        return "DETERMINISTIC".equals(finding.source())
                && "WORDING".equals(finding.attributesJson().get("category"));
    }

    private static FindingFixTarget groupedDailyLogLocation(String location) {
        var matcher = Pattern.compile(".*groups\\[(\\d+)]\\.entries\\[(\\d+)]\\.supervisionContent$")
                .matcher(location);
        if (!matcher.matches()) {
            return null;
        }
        return FindingFixTarget.dailyGrouped(integerOrNull(matcher.group(1)), integerOrNull(matcher.group(2)));
    }

    private static FindingFixTarget flatDailyLogLocation(String location) {
        var matcher = Pattern.compile(".*DAILY_LOG\\.entries\\[(\\d+)]\\.supervisionContent$")
                .matcher(location);
        if (!matcher.matches()) {
            return null;
        }
        return FindingFixTarget.dailyFlat(integerOrNull(matcher.group(1)));
    }

    private static Integer integerOrNull(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String fixReplacement(ReportPreflightReviewFinding finding) {
        var replacement = textValue(finding.attributesJson().get("replacement"));
        if (!replacement.isBlank()) {
            return safeReplacement(replacement) ? replacement : "";
        }
        var suggestion = textValue(finding.attributesJson().get("suggestion"));
        return safeReplacement(suggestion) ? suggestion : "";
    }

    private static String textValue(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean safeReplacement(String value) {
        if (value == null) {
            return false;
        }
        var text = value.trim();
        if (text.isBlank() || text.length() > 2000) {
            return false;
        }
        var instructionMarkers = List.of(
                "하십시오",
                "하세요",
                "바랍니다",
                "수정하십시오",
                "수정 바랍니다",
                "수정하고",
                "다듬으십시오",
                "기재하십시오",
                "기재하여",
                "권고합니다",
                "첨부합니다",
                "확인 후",
                "명확히 기재",
                "문장을 완성",
                "문장으로 수정",
                "최종 문장으로");
        return instructionMarkers.stream().noneMatch(text::contains);
    }

    private static void applyReplacement(Map<String, Object> payload, FindingFixTarget target, String replacement) {
        if ("REMARKS".equals(target.stepCode())) {
            payload.put(target.payloadKey(), replacement);
            return;
        }
        if ("DAILY_LOG".equals(target.stepCode())) {
            applyDailyLogReplacement(payload, target, replacement);
            return;
        }
        throw new BadRequestException("Unsupported preflight fix target");
    }

    private static Map<String, Object> fixAppliedEventPayload(
            Long reportId,
            Long reviewRunId,
            Long findingId,
            FindingFixTarget target
    ) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("reportId", reportId);
        payload.put("reviewRunId", reviewRunId);
        payload.put("findingId", findingId);
        payload.put("stepCode", target.stepCode());
        payload.put("target", target.description());
        if (target.payloadKey() != null) {
            payload.put("payloadKey", target.payloadKey());
        }
        if (target.groupIndex() != null) {
            payload.put("groupIndex", target.groupIndex());
        }
        if (target.entryIndex() != null) {
            payload.put("entryIndex", target.entryIndex());
        }
        if (target.flatEntryIndex() != null) {
            payload.put("flatEntryIndex", target.flatEntryIndex());
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    private static void applyDailyLogReplacement(Map<String, Object> payload, FindingFixTarget target, String replacement) {
        var dailyItems = mutableChildMap(payload, "dailyItems");
        var groups = mutableChildList(dailyItems, "groups");
        if (target.flatEntryIndex() != null) {
            var remaining = target.flatEntryIndex();
            for (Object groupValue : groups) {
                var group = asMutableMap(groupValue);
                var entries = mutableChildList(group, "entries");
                if (remaining < entries.size()) {
                    var entry = asMutableMap(entries.get(remaining));
                    entry.put("supervisionContent", replacement);
                    entries.set(remaining, entry);
                    return;
                }
                remaining -= entries.size();
            }
            throw new BadRequestException(
                    "REPORT_PREFLIGHT_FIX_TARGET_NOT_FOUND",
                    "errors.reportPreflight.fixTargetNotFound",
                    "Preflight fix target entry was not found.",
                    Map.of("flatEntryIndex", target.flatEntryIndex()));
        }
        if (target.groupIndex() == null || target.entryIndex() == null
                || target.groupIndex() < 0 || target.entryIndex() < 0
                || target.groupIndex() >= groups.size()) {
            throw new BadRequestException(
                    "REPORT_PREFLIGHT_FIX_TARGET_NOT_FOUND",
                    "errors.reportPreflight.fixTargetNotFound",
                    "Preflight fix target group was not found.",
                    Map.of("groupIndex", target.groupIndex()));
        }
        var group = asMutableMap(groups.get(target.groupIndex()));
        var entries = mutableChildList(group, "entries");
        if (target.entryIndex() >= entries.size()) {
            throw new BadRequestException(
                    "REPORT_PREFLIGHT_FIX_TARGET_NOT_FOUND",
                    "errors.reportPreflight.fixTargetNotFound",
                    "Preflight fix target entry was not found.",
                    Map.of("groupIndex", target.groupIndex(), "entryIndex", target.entryIndex()));
        }
        var entry = asMutableMap(entries.get(target.entryIndex()));
        entry.put("supervisionContent", replacement);
        entries.set(target.entryIndex(), entry);
    }

    @SuppressWarnings("unchecked")
    private static LinkedHashMap<String, Object> mutableMap(Map<String, Object> source) {
        var result = new LinkedHashMap<String, Object>();
        for (var entry : source.entrySet()) {
            result.put(entry.getKey(), mutableValue(entry.getValue()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object mutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), mutableValue(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list.stream().map(ReportPreflightReviewService::mutableValue).toList());
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static LinkedHashMap<String, Object> asMutableMap(Object value) {
        if (value instanceof LinkedHashMap<?, ?> linked) {
            return (LinkedHashMap<String, Object>) linked;
        }
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), mutableValue(entry.getValue()));
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static LinkedHashMap<String, Object> mutableChildMap(Map<String, Object> parent, String key) {
        var child = asMutableMap(parent.get(key));
        parent.put(key, child);
        return child;
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<Object> mutableChildList(Map<String, Object> parent, String key) {
        var value = parent.get(key);
        if (value instanceof ArrayList<?> arrayList) {
            return (ArrayList<Object>) arrayList;
        }
        if (value instanceof List<?> list) {
            var result = new ArrayList<>(list.stream().map(ReportPreflightReviewService::mutableValue).toList());
            parent.put(key, result);
            return result;
        }
        var result = new ArrayList<Object>();
        parent.put(key, result);
        return result;
    }

    private record FindingFixTarget(
            String stepCode,
            String payloadKey,
            Integer groupIndex,
            Integer entryIndex,
            Integer flatEntryIndex
    ) {
        private static FindingFixTarget remarks(String payloadKey) {
            return new FindingFixTarget("REMARKS", payloadKey, null, null, null);
        }

        private static FindingFixTarget dailyGrouped(Integer groupIndex, Integer entryIndex) {
            return new FindingFixTarget("DAILY_LOG", null, groupIndex, entryIndex, null);
        }

        private static FindingFixTarget dailyFlat(Integer flatEntryIndex) {
            return new FindingFixTarget("DAILY_LOG", null, null, null, flatEntryIndex);
        }

        private String description() {
            if ("REMARKS".equals(stepCode)) {
                return stepCode + "." + payloadKey;
            }
            if (flatEntryIndex != null) {
                return stepCode + ".entries[" + flatEntryIndex + "].supervisionContent";
            }
            return stepCode + ".groups[" + groupIndex + "].entries[" + entryIndex + "].supervisionContent";
        }
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
