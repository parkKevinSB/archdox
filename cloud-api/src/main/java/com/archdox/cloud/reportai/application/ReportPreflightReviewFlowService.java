package com.archdox.cloud.reportai.application;

import com.archdox.cloud.engine.application.ArchDoxEngineFinding;
import com.archdox.cloud.engine.application.EngineValidationResult;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.reportai.domain.ReportPreflightFindingResolutionStatus;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewRequest;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewFindingRepository;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import com.archdox.cloud.worker.engine.EngineWorkerActionSubmissionRequest;
import com.archdox.cloud.worker.engine.EngineWorkerActionSubmissionResult;
import com.archdox.cloud.worker.engine.EngineWorkerActionSubmissionService;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerRequestContext;
import com.archdox.worker.domain.ArchDoxWorkerRequestSource;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportPreflightReviewFlowService {
    private static final String REPORT_TYPE_CHECKLIST = "CONSTRUCTION_SUPERVISION_CHECKLIST";

    private final InspectionReportRepository reportRepository;
    private final ReportPreflightReviewRunRepository runRepository;
    private final ReportPreflightReviewFindingRepository findingRepository;
    private final ReportPreflightDeterministicValidator deterministicValidator;
    private final ReportPreflightEngineBoundaryService engineBoundaryService;
    private final EngineWorkerActionSubmissionService workerActionSubmissionService;
    private final ReportPreflightAiHarnessFlowService aiHarnessFlowService;
    private final ReportPreflightLegalReviewHarnessService legalReviewHarnessService;
    private final OperationEventService operationEventService;
    private final ReportPreflightFieldValueResolver fieldValueResolver;

    public ReportPreflightReviewFlowService(
            InspectionReportRepository reportRepository,
            ReportPreflightReviewRunRepository runRepository,
            ReportPreflightReviewFindingRepository findingRepository,
            ReportPreflightDeterministicValidator deterministicValidator,
            ReportPreflightEngineBoundaryService engineBoundaryService,
            EngineWorkerActionSubmissionService workerActionSubmissionService,
            ReportPreflightAiHarnessFlowService aiHarnessFlowService,
            ReportPreflightLegalReviewHarnessService legalReviewHarnessService,
            OperationEventService operationEventService,
            ReportPreflightFieldValueResolver fieldValueResolver
    ) {
        this.reportRepository = reportRepository;
        this.runRepository = runRepository;
        this.findingRepository = findingRepository;
        this.deterministicValidator = deterministicValidator;
        this.engineBoundaryService = engineBoundaryService;
        this.workerActionSubmissionService = workerActionSubmissionService;
        this.aiHarnessFlowService = aiHarnessFlowService;
        this.legalReviewHarnessService = legalReviewHarnessService;
        this.operationEventService = operationEventService;
        this.fieldValueResolver = fieldValueResolver;
    }

    @Transactional(readOnly = true)
    public void validateContext(ReportPreflightReviewRequest request) {
        reportRepository.findByIdAndOfficeId(request.reportId(), request.officeId())
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
        runRepository.findByIdAndOfficeIdAndReportId(request.reviewRunId(), request.officeId(), request.reportId())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
    }

    @Transactional
    public ReportPreflightValidationResult runDeterministicValidation(ReportPreflightReviewRequest request) {
        var report = reportRepository.findByIdAndOfficeId(request.reportId(), request.officeId())
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
        var run = runRepository.findByIdAndOfficeIdAndReportId(request.reviewRunId(), request.officeId(), request.reportId())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
        run.markRunning(OffsetDateTime.now());
        findingRepository.deleteByReviewRunId(run.id());
        var deterministicResult = deterministicValidator.validate(report);
        var engineResult = engineBoundaryService.validate(report, request.requestedBy());
        var workerActionSubmission = workerActionSubmissionService.submitAfterCommit(
                engineResult,
                workerActionSubmissionRequest(request, report));
        var aiReviewPlanned = !REPORT_TYPE_CHECKLIST.equals(report.reportType())
                && aiHarnessFlowService.canCreate(report, request.requestedBy());
        var result = withCarriedOpenFindings(
                request,
                run,
                combinedResult(deterministicResult, engineResult),
                aiReviewPlanned);
        for (var finding : result.findings()) {
            var now = OffsetDateTime.now();
            var entity = new ReportPreflightReviewFinding(
                    request.officeId(),
                    request.reviewRunId(),
                    request.reportId(),
                    finding.source(),
                    finding.code(),
                    finding.severity(),
                    finding.location(),
                    finding.message(),
                    finding.evidence(),
                    finding.attributes(),
                    now);
            ReportPreflightFindingClassifier.autoResolveOnCreate(entity, request.requestedBy(), now);
            findingRepository.save(entity);
        }
        if (result.blocksGeneration() && !aiReviewPlanned) {
            run.markNeedsAttention("DETERMINISTIC_PREFLIGHT_BLOCKED", OffsetDateTime.now());
        } else {
            run.markRunning(OffsetDateTime.now());
        }
        operationEventService.record(
                request.officeId(),
                result.blocksGeneration() ? OperationEventSeverity.WARN : OperationEventSeverity.INFO,
                "REPORT_PREFLIGHT_REVIEW_DETERMINISTIC_VALIDATION",
                "report-preflight-review",
                workflowKey(request),
                "REPORT_PREFLIGHT_REVIEW_RUN",
                request.reviewRunId(),
                request.requestedBy(),
                null,
                result.blocksGeneration()
                        ? "Report preflight validation found blocking issues."
                        : "Report preflight validation passed.",
                validationEventMetadata(request, result, engineResult, workerActionSubmission, aiReviewPlanned));
        return result;
    }

    @Transactional(readOnly = true)
    public boolean canSubmitAiHarness(ReportPreflightReviewRequest request) {
        var run = runRepository.findByIdAndOfficeIdAndReportId(request.reviewRunId(), request.officeId(), request.reportId())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
        if (run.hasHarness()) {
            return !run.terminal() && !run.harnessTerminal();
        }
        if (run.terminal()) {
            return false;
        }
        var report = reportRepository.findByIdAndOfficeId(request.reportId(), request.officeId())
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
        if (REPORT_TYPE_CHECKLIST.equals(report.reportType())) {
            return false;
        }
        return aiHarnessFlowService.canCreate(report, request.requestedBy());
    }

    @Transactional
    public AiHarnessFlow createAiHarnessFlow(ReportPreflightReviewRequest request) {
        var report = reportRepository.findByIdAndOfficeId(request.reportId(), request.officeId())
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
        var run = runRepository.findByIdAndOfficeIdAndReportId(request.reviewRunId(), request.officeId(), request.reportId())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
        if (run.hasHarness() || run.terminal()) {
            return null;
        }
        var findings = findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(request.officeId(), request.reviewRunId());
        return aiHarnessFlowService.create(report, run, findings);
    }

    @Transactional
    public void markAiHarnessSubmitted(ReportPreflightReviewRequest request) {
        operationEventService.record(
                request.officeId(),
                OperationEventSeverity.INFO,
                "REPORT_PREFLIGHT_AI_REVIEW_SUBMITTED",
                "report-preflight-review",
                workflowKey(request),
                "REPORT_PREFLIGHT_REVIEW_RUN",
                request.reviewRunId(),
                request.requestedBy(),
                null,
                "Report preflight review flow submitted the AI harness.",
                Map.of("reportId", request.reportId(), "reviewRunId", request.reviewRunId()));
    }

    @Transactional(readOnly = true)
    public boolean isAiHarnessTerminal(ReportPreflightReviewRequest request) {
        var run = runRepository.findByIdAndOfficeIdAndReportId(request.reviewRunId(), request.officeId(), request.reportId())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
        if (!run.hasHarness()) {
            return true;
        }
        if (run.terminal() && !run.harnessTerminal()) {
            return true;
        }
        return run.harnessTerminal();
    }

    @Transactional
    public void summarizeAiResult(ReportPreflightReviewRequest request) {
        var run = runRepository.findByIdAndOfficeIdAndReportId(request.reviewRunId(), request.officeId(), request.reportId())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
        if (run.status() == com.archdox.cloud.reportai.domain.ReportPreflightReviewStatus.FAILED) {
            return;
        }
        if (run.hasHarness() && !run.harnessTerminal()) {
            return;
        }
        var findings = findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(request.officeId(), request.reviewRunId());
        long attentionCount = findings.stream()
                .filter(finding -> requiresResolutionForGeneration(finding)
                        && finding.resolutionStatus() == com.archdox.cloud.reportai.domain.ReportPreflightFindingResolutionStatus.OPEN)
                .count();
        if (attentionCount > 0) {
            run.markNeedsAttention("AI_PREFLIGHT_NEEDS_HUMAN_REVIEW", OffsetDateTime.now());
        } else {
            run.markPassed("AI_PREFLIGHT_PASSED", OffsetDateTime.now());
        }
        operationEventService.record(
                request.officeId(),
                attentionCount > 0 ? OperationEventSeverity.WARN : OperationEventSeverity.INFO,
                "REPORT_PREFLIGHT_AI_REVIEW_SUMMARIZED",
                "report-preflight-review",
                workflowKey(request),
                "REPORT_PREFLIGHT_REVIEW_RUN",
                request.reviewRunId(),
                request.requestedBy(),
                null,
                attentionCount > 0
                        ? "Report preflight AI dry-run produced findings that need human review."
                        : "Report preflight AI review passed.",
                Map.of(
                        "reportId", request.reportId(),
                        "reviewRunId", request.reviewRunId(),
                        "findingCount", findings.size(),
                        "attentionFindingCount", attentionCount));
    }

    @Transactional(readOnly = true)
    public boolean shouldRunSourceBackedLegalReview(ReportPreflightReviewRequest request) {
        var run = runRepository.findByIdAndOfficeIdAndReportId(request.reviewRunId(), request.officeId(), request.reportId())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
        if (run.status() == com.archdox.cloud.reportai.domain.ReportPreflightReviewStatus.FAILED) {
            return false;
        }
        if ("DETERMINISTIC_PREFLIGHT_BLOCKED".equals(run.terminalReason())) {
            return false;
        }
        var report = reportRepository.findByIdAndOfficeId(request.reportId(), request.officeId())
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
        if (REPORT_TYPE_CHECKLIST.equals(report.reportType())) {
            return false;
        }
        return true;
    }

    public ReportPreflightLegalReviewHarnessService.LegalReviewSubmission submitSourceBackedLegalReview(
            ReportPreflightReviewRequest request
    ) {
        return legalReviewHarnessService.submit(request);
    }

    public boolean isSourceBackedLegalReviewTerminal(AiHarnessFlow flow) {
        return legalReviewHarnessService.terminal(flow);
    }

    public void timeoutSourceBackedLegalReview(
            ReportPreflightReviewRequest request,
            AiHarnessFlow flow
    ) {
        legalReviewHarnessService.timeout(request, flow);
    }

    public void completeSourceBackedLegalReview(
            ReportPreflightReviewRequest request,
            AiHarnessFlow flow
    ) {
        legalReviewHarnessService.complete(request, flow);
    }

    @Transactional
    public void complete(ReportPreflightReviewRequest request) {
        operationEventService.record(
                request.officeId(),
                OperationEventSeverity.INFO,
                "REPORT_PREFLIGHT_REVIEW_COMPLETED",
                "report-preflight-review",
                workflowKey(request),
                "REPORT_PREFLIGHT_REVIEW_RUN",
                request.reviewRunId(),
                request.requestedBy(),
                null,
                "Report preflight review flow completed.",
                Map.of("reportId", request.reportId(), "reviewRunId", request.reviewRunId()));
    }

    @Transactional
    public void fail(ReportPreflightReviewRequest request, String reason) {
        runRepository.findByIdAndOfficeIdAndReportId(request.reviewRunId(), request.officeId(), request.reportId())
                .ifPresent(run -> run.markFailed(reasonOf(reason), OffsetDateTime.now()));
        operationEventService.record(
                request.officeId(),
                OperationEventSeverity.ERROR,
                "REPORT_PREFLIGHT_REVIEW_FAILED",
                "report-preflight-review",
                workflowKey(request),
                "REPORT_PREFLIGHT_REVIEW_RUN",
                request.reviewRunId(),
                request.requestedBy(),
                null,
                "Report preflight review flow failed.",
                Map.of(
                        "reportId", request.reportId(),
                        "reviewRunId", request.reviewRunId(),
                        "reason", reasonOf(reason)));
    }

    private static String workflowKey(ReportPreflightReviewRequest request) {
        return "report:" + request.reportId() + ":preflight-run:" + request.reviewRunId();
    }

    private static ReportPreflightValidationResult combinedResult(
            ReportPreflightValidationResult deterministicResult,
            EngineValidationResult engineResult
    ) {
        var findings = new ArrayList<ReportPreflightFinding>();
        findings.addAll(deterministicResult.findings());
        findings.addAll(engineResult.findings().stream()
                .map(finding -> toPreflightFinding(finding, engineResult))
                .toList());
        legalReferenceSummaryFinding(engineResult).ifPresent(findings::add);
        return new ReportPreflightValidationResult(List.copyOf(findings));
    }

    private static java.util.Optional<ReportPreflightFinding> legalReferenceSummaryFinding(EngineValidationResult engineResult) {
        if (engineResult.legalReferences().isEmpty()) {
            return java.util.Optional.empty();
        }
        var referenceIds = engineResult.legalReferences().stream()
                .map(reference -> text(reference.get("referenceId")))
                .filter(value -> !value.isBlank())
                .toList();
        var legalReferenceDetails = engineResult.legalReferences().stream()
                .map(ReportPreflightReviewFlowService::legalReferenceDetailLine)
                .filter(line -> !line.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("engineRunId", engineResult.engineRunId());
        attributes.put("engineStatus", engineResult.status().name());
        attributes.put("enginePhase", engineResult.enginePhase());
        attributes.put("category", "LEGAL_CONTEXT");
        attributes.put("approvalRequired", "false");
        attributes.put("engine.legalReferenceCount", String.valueOf(engineResult.legalReferences().size()));
        if (!referenceIds.isEmpty()) {
            attributes.put("legalReferences", String.join(",", referenceIds));
        }
        if (!legalReferenceDetails.isBlank()) {
            attributes.put("legalReferenceDetails", legalReferenceDetails);
        }
        return java.util.Optional.of(new ReportPreflightFinding(
                "DETERMINISTIC",
                "LEGAL_EVIDENCE_CONTEXT_USED",
                "INFO",
                "LEGAL_CONTEXT",
                "법령 근거를 사용해 생성 전 검토를 수행했습니다.",
                referenceIds.isEmpty() ? "legalReferenceCount=" + engineResult.legalReferences().size() : "legalReferences=" + String.join(",", referenceIds),
                Map.copyOf(attributes)));
    }

    private ReportPreflightValidationResult withCarriedOpenFindings(
            ReportPreflightReviewRequest request,
            com.archdox.cloud.reportai.domain.ReportPreflightReviewRun currentRun,
            ReportPreflightValidationResult currentResult,
            boolean carryAiFindings
    ) {
        var findings = new ArrayList<>(currentResult.findings());
        var currentKeys = new LinkedHashSet<String>();
        for (var finding : findings) {
            currentKeys.add(findingKey(finding.source(), finding.code(), finding.location()));
        }
        var previousRun = runRepository.findByOfficeIdAndReportIdOrderByRequestedAtDesc(
                        request.officeId(),
                        request.reportId())
                .stream()
                .filter(run -> !java.util.Objects.equals(run.id(), currentRun.id()))
                .filter(run -> run.reportRevision() == currentRun.reportRevision())
                .findFirst();
        if (previousRun.isEmpty()) {
            return currentResult;
        }
        var carried = findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(request.officeId(), previousRun.get().id())
                .stream()
                .filter(finding -> finding.resolutionStatus() == ReportPreflightFindingResolutionStatus.OPEN)
                .filter(finding -> carryAiFindings || !isAiGeneratedFinding(finding))
                .filter(finding -> !currentKeys.contains(findingKey(finding.source(), finding.code(), finding.location())))
                .filter(this::fieldValueStillMatches)
                .map(finding -> carriedFinding(finding, previousRun.get().id()))
                .toList();
        if (carried.isEmpty()) {
            return currentResult;
        }
        findings.addAll(carried);
        return new ReportPreflightValidationResult(findings);
    }

    private static boolean isAiGeneratedFinding(ReportPreflightReviewFinding finding) {
        return "AI".equals(finding.source()) || "LEGAL_REVIEW".equals(finding.source());
    }

    private boolean fieldValueStillMatches(ReportPreflightReviewFinding finding) {
        var previousHash = text(finding.attributesJson().get("fieldValueHash"));
        if (previousHash.isBlank()) {
            return false;
        }
        return fieldValueResolver.resolveHash(finding.reportId(), finding.location())
                .map(previousHash::equals)
                .orElse(false);
    }

    private static ReportPreflightFinding carriedFinding(
            ReportPreflightReviewFinding finding,
            Long previousRunId
    ) {
        var attributes = new LinkedHashMap<String, String>(finding.attributesJson());
        attributes.put("carriedOver", "true");
        attributes.put("carriedOverFromRunId", String.valueOf(previousRunId));
        if (finding.id() != null) {
            attributes.put("carriedOverFromFindingId", String.valueOf(finding.id()));
        }
        return new ReportPreflightFinding(
                finding.source(),
                finding.code(),
                finding.severity(),
                finding.location(),
                finding.message(),
                finding.evidence(),
                Map.copyOf(attributes));
    }

    private static String findingKey(String source, String code, String location) {
        return text(source) + "|" + text(code) + "|" + text(location);
    }

    private static EngineWorkerActionSubmissionRequest workerActionSubmissionRequest(
            ReportPreflightReviewRequest request,
            com.archdox.cloud.inspection.domain.InspectionReport report
    ) {
        return new EngineWorkerActionSubmissionRequest(
                null,
                ArchDoxWorkerRequestSource.UI,
                "Report preflight Engine follow-up action",
                new ArchDoxWorkerRequestContext(
                        request.requestedBy(),
                        request.officeId(),
                        report.projectId(),
                        report.siteId(),
                        report.id(),
                        null,
                        "ko-KR"),
                workerActionPayload(request, report),
                Set.of(ArchDoxWorkerActionType.RUN_PREFLIGHT_REVIEW));
    }

    private static Map<String, Object> workerActionPayload(
            ReportPreflightReviewRequest request,
            com.archdox.cloud.inspection.domain.InspectionReport report
    ) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("officeId", request.officeId());
        payload.put("reportId", request.reportId());
        payload.put("reviewRunId", request.reviewRunId());
        payload.put("projectId", report.projectId());
        if (report.siteId() != null) {
            payload.put("siteId", report.siteId());
        }
        if (request.requestedBy() != null) {
            payload.put("requestedBy", request.requestedBy());
        }
        return Map.copyOf(payload);
    }

    private static Map<String, Object> validationEventMetadata(
            ReportPreflightReviewRequest request,
            ReportPreflightValidationResult result,
            EngineValidationResult engineResult,
            EngineWorkerActionSubmissionResult workerActionSubmission,
            boolean aiReviewPlanned
    ) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("reportId", request.reportId());
        metadata.put("reviewRunId", request.reviewRunId());
        metadata.put("findingCount", result.findings().size());
        metadata.put("blockingFindingCount", result.blockingFindingCount());
        metadata.put("engineRunId", engineResult.engineRunId());
        metadata.put("engineStatus", engineResult.status().name());
        metadata.put("engineFindingCount", engineResult.findings().size());
        metadata.put("workerActionCandidates", workerActionSubmission.candidates());
        metadata.put("workerActionSubmission", workerActionSubmission.toMetadata());
        metadata.put("aiReviewPlanned", aiReviewPlanned);
        metadata.put("aiReviewSkipped", !aiReviewPlanned);
        return Map.copyOf(metadata);
    }

    private static ReportPreflightFinding toPreflightFinding(
            ArchDoxEngineFinding finding,
            EngineValidationResult engineResult
    ) {
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("engineRunId", engineResult.engineRunId());
        attributes.put("engineStatus", engineResult.status().name());
        attributes.put("enginePhase", engineResult.enginePhase());
        attributes.put("engineSource", finding.source() == null ? "" : finding.source().name());
        attributes.put("engineCategory", finding.category());
        if (!finding.legalReferences().isEmpty()) {
            attributes.put("legalReferences", String.join(",", finding.legalReferences()));
            var legalReferenceDetails = legalReferenceDetails(finding, engineResult);
            if (!legalReferenceDetails.isBlank()) {
                attributes.put("legalReferenceDetails", legalReferenceDetails);
            }
        }
        if (!engineResult.nextActions().isEmpty()) {
            attributes.put("engine.nextActions", String.join(",", engineResult.nextActions()));
        }
        finding.metadata().forEach((key, value) -> attributes.put("engine." + key, String.valueOf(value)));
        return new ReportPreflightFinding(
                "DETERMINISTIC",
                finding.code(),
                severity(finding.severity()),
                finding.location(),
                finding.message(),
                evidence(finding),
                Map.copyOf(attributes));
    }

    private static boolean requiresResolutionForGeneration(ReportPreflightReviewFinding finding) {
        return ReportPreflightFindingClassifier.requiresResolutionForGeneration(finding);
    }

    private static String reasonOf(String reason) {
        if (reason != null && !reason.isBlank()) {
            return reason;
        }
        return "Report preflight review failed";
    }

    private static String severity(String severity) {
        if (severity == null || severity.isBlank()) {
            return "LOW";
        }
        return severity.trim().toUpperCase();
    }

    private static String evidence(ArchDoxEngineFinding finding) {
        if (!finding.legalReferences().isEmpty()) {
            return "legalReferences=" + String.join(",", finding.legalReferences());
        }
        var engineCheck = finding.metadata().get("engineCheck");
        if (engineCheck != null) {
            return String.valueOf(engineCheck);
        }
        return "engineRunId";
    }

    private static String legalReferenceDetails(
            ArchDoxEngineFinding finding,
            EngineValidationResult engineResult
    ) {
        var ids = new LinkedHashSet<>(finding.legalReferences());
        return engineResult.legalReferences().stream()
                .filter(reference -> ids.contains(text(reference.get("referenceId"))))
                .map(ReportPreflightReviewFlowService::legalReferenceDetailLine)
                .filter(line -> !line.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private static String legalReferenceDetailLine(Map<String, Object> reference) {
        var metadata = objectMap(reference.get("metadata"));
        return String.join("\t",
                safeCell(text(reference.get("referenceId"))),
                safeCell(legalReferenceLabel(reference)),
                safeCell(text(metadata.get("resolutionSource"))),
                safeCell(text(reference.get("bindingScope"))),
                safeCell(text(reference.get("bindingKey"))),
                safeCell(text(reference.get("relevance"))),
                safeCell(text(reference.get("catalogCode"))),
                safeCell(text(reference.get("catalogVersion"))),
                safeCell(text(reference.get("checklistItemCode"))));
    }

    private static String legalReferenceLabel(Map<String, Object> reference) {
        return List.of(
                        text(reference.get("actName")),
                        text(reference.get("articleNo")),
                        text(reference.get("articleTitle")))
                .stream()
                .filter(value -> !value.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse(text(reference.get("referenceId")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String safeCell(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
