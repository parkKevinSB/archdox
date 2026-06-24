package com.archdox.cloud.reportai.application;

import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.application.AiModelCallMetadata;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStep;
import com.archdox.cloud.inspection.application.DailySupervisionContentFormatter;
import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewFinding;
import com.archdox.cloud.reportai.domain.ReportPreflightReviewRun;
import com.archdox.cloud.reportai.flow.ReportPreflightLegalReviewAiWorker;
import com.archdox.cloud.reportai.flow.ReportPreflightReviewRequest;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewFindingRepository;
import com.archdox.cloud.reportai.infra.ReportPreflightReviewRunRepository;
import com.archdox.documentai.ReportPreflightFindingSummary;
import com.archdox.legalai.SourceBackedLegalReviewHarnessFactory;
import com.archdox.legalai.SourceBackedLegalReviewInput;
import com.archdox.legalai.SourceBackedLegalReviewIssue;
import com.archdox.legalai.SourceBackedLegalReviewResult;
import com.archdox.legalai.SourceBackedLegalReviewStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlow;
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlowFactory;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.refine.MaxAttemptsRefinePolicy;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStore;
import io.github.parkkevinsb.flower.ai.harness.spi.TraceListener;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReportPreflightLegalReviewHarnessService {
    public static final String SOURCE = "LEGAL_REVIEW";
    public static final String REVIEW_MODE = "SOURCE_BACKED_LEGAL_REVIEW_DRAFT";
    private static final Duration WAIT_GRACE = Duration.ofSeconds(3);
    private static final int LEGAL_REFERENCE_REVIEW_LIMIT = 16;

    private final InspectionReportRepository reportRepository;
    private final InspectionReportStepRepository stepRepository;
    private final ReportPreflightReviewRunRepository runRepository;
    private final ReportPreflightReviewFindingRepository findingRepository;
    private final ReportPhotoEvidenceStatusService photoEvidenceStatusService;
    private final AiHarnessPolicyExecutionService policyExecutionService;
    private final ReportPreflightLegalReviewAiWorker aiWorker;
    private final AiModelGateway aiModelGateway;
    private final ObjectMapper objectMapper;
    private final TraceListener aiHarnessTraceListener;
    private final OperationEventService operationEventService;
    private final TransactionTemplate transactionTemplate;

    public ReportPreflightLegalReviewHarnessService(
            InspectionReportRepository reportRepository,
            InspectionReportStepRepository stepRepository,
            ReportPreflightReviewRunRepository runRepository,
            ReportPreflightReviewFindingRepository findingRepository,
            ReportPhotoEvidenceStatusService photoEvidenceStatusService,
            AiHarnessPolicyExecutionService policyExecutionService,
            ReportPreflightLegalReviewAiWorker aiWorker,
            AiModelGateway aiModelGateway,
            ObjectMapper objectMapper,
            TraceListener aiHarnessTraceListener,
            OperationEventService operationEventService,
            PlatformTransactionManager transactionManager
    ) {
        this.reportRepository = reportRepository;
        this.stepRepository = stepRepository;
        this.runRepository = runRepository;
        this.findingRepository = findingRepository;
        this.photoEvidenceStatusService = photoEvidenceStatusService;
        this.policyExecutionService = policyExecutionService;
        this.aiWorker = aiWorker;
        this.aiModelGateway = aiModelGateway;
        this.objectMapper = objectMapper;
        this.aiHarnessTraceListener = aiHarnessTraceListener;
        this.operationEventService = operationEventService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public LegalReviewSubmission submit(ReportPreflightReviewRequest request) {
        var context = context(request);
        var references = legalReferences(context);
        if (references.isEmpty()) {
            saveSingleFinding(context, insufficientContextFinding(context, List.of()));
            recordEvent(context, OperationEventSeverity.WARN, "REPORT_PREFLIGHT_LEGAL_REVIEW_INSUFFICIENT_CONTEXT",
                    "Source-backed legal review skipped because no legal references were available.",
                    Map.of("legalReferenceCount", 0));
            return LegalReviewSubmission.completed();
        }

        var policy = policyExecutionService.resolve(AiHarnessPolicyKey.SOURCE_BACKED_LEGAL_REVIEW);
        if (!policy.runnable()) {
            saveSingleFinding(context, skippedFinding(context, references, policy.unavailableReason()));
            recordEvent(context, OperationEventSeverity.WARN, "REPORT_PREFLIGHT_LEGAL_REVIEW_SKIPPED",
                    "Source-backed legal review AI policy is not runnable.",
                    Map.of("reason", policy.unavailableReason(), "legalReferenceCount", references.size()));
            return LegalReviewSubmission.completed();
        }

        var plan = policy.plan();
        try {
            policyExecutionService.requireWithinBudget(plan);
        } catch (BadRequestException ex) {
            saveSingleFinding(context, skippedFinding(context, references, ex.getMessage()));
            recordEvent(context, OperationEventSeverity.WARN, "REPORT_PREFLIGHT_LEGAL_REVIEW_BUDGET_SKIPPED",
                    "Source-backed legal review AI was skipped because the configured budget was exhausted.",
                    Map.of(
                            "reason", ex.getMessage(),
                            "errorCode", ex.code(),
                            "legalReferenceCount", references.size()));
            return LegalReviewSubmission.completed();
        }
        var spec = new SourceBackedLegalReviewHarnessFactory(objectMapper).spec(
                (findings, ctx) -> {
                },
                AiHarnessRunStore.noop(),
                new MaxAttemptsRefinePolicy(plan.maxAttempts()),
                aiHarnessTraceListener);
        var flow = new AiHarnessFlowFactory<>(aiModelGateway, spec, java.time.Instant::now)
                .createFlow(input(context, references), AiHarnessFlowFactory.RunOverrides.builder()
                        .modelId(plan.modelId())
                        .timeout(plan.timeout())
                        .providerOptions(AiModelCallMetadata.options(
                                context.report().officeId(),
                                context.run().requestedBy(),
                                AiHarnessPolicyKey.SOURCE_BACKED_LEGAL_REVIEW.name(),
                                "report-source-backed-legal-review",
                                workflowKey(context),
                                "REPORT_PREFLIGHT_REVIEW_RUN",
                                context.run().id(),
                                Map.of(
                                        "archdox.reportId", context.report().id(),
                                        "archdox.reviewRunId", context.run().id(),
                                        "archdox.policyKey", AiHarnessPolicyKey.SOURCE_BACKED_LEGAL_REVIEW.name(),
                                        "archdox.reviewMode", REVIEW_MODE),
                                plan.maxOutputTokens()))
                        .build());

        aiWorker.submit(flow);
        recordEvent(context, OperationEventSeverity.INFO, "REPORT_PREFLIGHT_LEGAL_REVIEW_AI_SUBMITTED",
                "Source-backed legal review AI harness was submitted.",
                Map.of(
                        "harnessRunId", flow.context().runId().value(),
                        "legalReferenceCount", references.size()));
        return LegalReviewSubmission.submitted(flow, plan.timeout().plus(WAIT_GRACE));
    }

    public boolean terminal(AiHarnessFlow flow) {
        return flow == null || flow.flow().state().isTerminal();
    }

    public void timeout(ReportPreflightReviewRequest request, AiHarnessFlow flow) {
        if (flow != null && !flow.flow().state().isTerminal()) {
            flow.flow().cancel();
        }
        var context = context(request);
        var references = legalReferences(context);
        saveSingleFinding(context, skippedFinding(context, references, "법령검토 AI 응답 시간이 초과되어 이번 법령검토를 생략했습니다. 다시 실행하면 재시도됩니다."));
        recordEvent(context, OperationEventSeverity.ERROR, "REPORT_PREFLIGHT_LEGAL_REVIEW_TIMEOUT",
                "Source-backed legal review AI timed out.",
                Map.of("harnessRunId", flow == null ? "" : flow.context().runId().value()));
    }

    public void complete(ReportPreflightReviewRequest request, AiHarnessFlow flow) {
        var context = context(request);
        var references = legalReferences(context);
        var coverage = legalReferenceCoverage(references, context);
        if (references.isEmpty()) {
            saveSingleFinding(context, insufficientContextFinding(context, List.of()));
            return;
        }
        if (flow.context().status() != AiHarnessRunStatus.SUCCEEDED) {
            var terminalReason = flow.context().terminalReason().orElse("법령검토 AI가 정상 완료되지 않았습니다.");
            saveSingleFinding(context, skippedFinding(context, references, userFacingAiFailureReason(terminalReason)));
            recordEvent(context, OperationEventSeverity.ERROR, "REPORT_PREFLIGHT_LEGAL_REVIEW_FAILED",
                    "Source-backed legal review AI failed.",
                    Map.of(
                            "harnessRunId", flow.context().runId().value(),
                            "status", flow.context().status().name(),
                            "terminalReason", terminalReason));
            return;
        }
        var result = result(flow.context().latestValidation());
        if (result.isEmpty()) {
            saveSingleFinding(context, skippedFinding(context, references, "법령검토 AI 응답 형식을 확인하지 못해 이번 법령검토를 생략했습니다. 다시 실행하면 재시도됩니다."));
            return;
        }
        var modelRequest = flow.context().currentRequest();
        var providerCode = modelRequest == null ? "" : modelRequest.modelId().provider();
        var modelId = modelRequest == null ? "" : modelRequest.modelId().asString();
        saveResult(context, references, coverage, result.get(), providerCode, modelId, flow.context().runId().value());
        recordEvent(context, OperationEventSeverity.INFO, "REPORT_PREFLIGHT_LEGAL_REVIEW_COMPLETED",
                "Source-backed legal review completed.",
                Map.of(
                        "harnessRunId", flow.context().runId().value(),
                        "status", result.get().status().name(),
                        "legalReferenceCount", references.size(),
                        "issueCount", result.get().issues().size()));
    }

    public record LegalReviewSubmission(
            boolean submitted,
            AiHarnessFlow flow,
            Duration timeout
    ) {
        private static LegalReviewSubmission completed() {
            return new LegalReviewSubmission(false, null, Duration.ZERO);
        }

        private static LegalReviewSubmission submitted(AiHarnessFlow flow, Duration timeout) {
            return new LegalReviewSubmission(true, flow, timeout);
        }
    }

    private LegalReviewContext context(ReportPreflightReviewRequest request) {
        var report = reportRepository.findByIdAndOfficeId(request.reportId(), request.officeId())
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
        var run = runRepository.findByIdAndOfficeIdAndReportId(request.reviewRunId(), request.officeId(), request.reportId())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
        var findings = findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(request.officeId(), request.reviewRunId()).stream()
                .filter(finding -> !SOURCE.equals(finding.source()))
                .filter(finding -> !"AI".equals(finding.source()))
                .toList();
        return new LegalReviewContext(request, report, run, findings);
    }

    private SourceBackedLegalReviewInput input(
            LegalReviewContext context,
            List<Map<String, Object>> references
    ) {
        var reportSnapshot = reportSnapshot(context.report());
        var steps = stepSnapshot(context.report());
        var evidenceChecklist = reportEvidenceChecklist(steps, reportSnapshot);
        var coverage = legalReferenceCoverage(references, evidenceChecklist);
        return new SourceBackedLegalReviewInput(
                String.valueOf(context.report().officeId()),
                String.valueOf(context.report().id()),
                context.report().reportType(),
                context.report().title(),
                context.report().contentRevision(),
                reportSnapshot,
                steps,
                findingSummaries(legalContextFindings(context.findings())),
                references,
                legalReviewContext(legalContextFindings(context.findings()), references, coverage, evidenceChecklist));
    }

    private Map<String, Object> reportSnapshot(InspectionReport report) {
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("id", report.id());
        snapshot.put("officeId", report.officeId());
        snapshot.put("projectId", report.projectId());
        snapshot.put("siteId", report.siteId() == null ? "" : report.siteId());
        snapshot.put("reportNo", report.reportNo() == null ? "" : report.reportNo());
        snapshot.put("reportType", report.reportType() == null ? "" : report.reportType());
        snapshot.put("title", report.title() == null ? "" : report.title());
        snapshot.put("status", report.status().name());
        snapshot.put("currentStep", report.currentStep() == null ? "" : report.currentStep());
        snapshot.put("contentRevision", report.contentRevision());
        snapshot.put("submittedRevision", report.submittedRevision() == null ? "" : report.submittedRevision());
        snapshot.put("photoEvidenceStatus", photoEvidenceStatusService.evaluate(report).toMap());
        return Map.copyOf(snapshot);
    }

    private Map<String, Object> stepSnapshot(InspectionReport report) {
        var snapshot = new LinkedHashMap<String, Object>();
        for (var step : stepRepository.findByReportIdOrderById(report.id())) {
            var stepValue = new LinkedHashMap<String, Object>();
            stepValue.put("payloadStorageMode", step.payloadStorageMode() == null ? "" : step.payloadStorageMode().name());
            stepValue.put("payload", step.payloadJson() == null ? Map.of() : step.payloadJson());
            stepValue.put("clientRevision", step.clientRevision());
            stepValue.put("savedAt", step.savedAt() == null ? "" : step.savedAt().toString());
            snapshot.put(step.stepCode() == null ? "" : step.stepCode(), stepValue);
        }
        return Map.copyOf(snapshot);
    }

    private List<ReportPreflightFindingSummary> findingSummaries(List<ReportPreflightReviewFinding> findings) {
        return findings.stream()
                .map(finding -> new ReportPreflightFindingSummary(
                        finding.source(),
                        finding.code(),
                        finding.severity(),
                        finding.location(),
                        finding.message(),
                        finding.evidence(),
                        finding.attributesJson()))
                .toList();
    }

    private Map<String, Object> legalReviewContext(
            List<ReportPreflightReviewFinding> findings,
            List<Map<String, Object>> references,
            LegalReferenceCoverage coverage,
            Map<String, Object> evidenceChecklist
    ) {
        var context = new LinkedHashMap<String, Object>();
        context.put("purpose", "SOURCE_BACKED_LEGAL_REVIEW_DRAFT");
        context.put("mode", REVIEW_MODE);
        context.put("sourceBackedOnly", true);
        context.put("legalReferenceCount", references.size());
        context.put("referenceCoverage", coverage.toMap());
        context.put("reportEvidenceChecklist", evidenceChecklist);
        context.put("legalFindings", findings.stream()
                .filter(finding -> finding.code().contains("LEGAL")
                        || !text(finding.attributesJson().get("legalReferences")).isBlank()
                        || !text(finding.attributesJson().get("legalReferenceDetails")).isBlank())
                .map(finding -> Map.of(
                        "source", finding.source(),
                        "code", finding.code(),
                        "severity", finding.severity(),
                        "location", finding.location() == null ? "" : finding.location(),
                        "message", finding.message(),
                        "evidence", finding.evidence() == null ? "" : finding.evidence()))
                .toList());
        context.put("instructions", List.of(
                "Use only supplied legal references as legal anchors.",
                "Return a dry-run legal review draft, not final legal advice.",
                "Do not modify report data or legal corpus.",
                "PASS is allowed only when referenceCoverage.passEligibleForPass is true.",
                "PASS is allowed only when referenceCoverage.passEligibility.finalEligible is true.",
                "Treat referenceCoverage.passBlockers as deterministic server blockers.",
                "A checklist/business-item PASS requires a PRIMARY BUSINESS_ITEM_ANCHOR.",
                "If references are only search candidates or generic anchors, return WARN or INSUFFICIENT_CONTEXT instead of PASS.",
                "Do not treat checklist binding plus photo evidence as technical-standard compliance.",
                "If technicalCriteriaReviewRequired=true and technicalCriteriaPassEligible=false, return WARN or INSUFFICIENT_CONTEXT instead of PASS.",
                "For vague material/performance notes, suggest final daily-log prose that names evidence classes such as specifications, test reports, approvals, certificates, product identity, or approved-vs-delivered material matching.",
                "Do not decide whether those documents really exist. Propose confirmation/attachment wording as a report draft that the supervising professional may approve and then support with documents."));
        return Map.copyOf(context);
    }

    private List<Map<String, Object>> legalReferences(LegalReviewContext context) {
        return legalReferences(context.findings(), selectedChecklistItemCodes(context.report()));
    }

    private List<Map<String, Object>> legalReferences(List<ReportPreflightReviewFinding> findings) {
        return legalReferences(findings, Set.of());
    }

    private List<Map<String, Object>> legalReferences(
            List<ReportPreflightReviewFinding> findings,
            Set<String> selectedChecklistItemCodes
    ) {
        var byId = new LinkedHashMap<String, Map<String, Object>>();
        for (var finding : findings) {
            var detailedReferences = parseLegalReferenceDetails(finding.attributesJson().get("legalReferenceDetails"));
            detailedReferences.stream()
                    .filter(reference -> referenceMatchesCurrentReport(reference, selectedChecklistItemCodes))
                    .forEach(reference -> mergeReference(byId, enrichReference(reference, finding)));
            if (detailedReferences.isEmpty()) {
                csvList(finding.attributesJson().get("legalReferences"))
                        .forEach(referenceId -> mergeReference(byId, enrichReference(Map.of("referenceId", referenceId), finding)));
            }
        }
        return byId.values().stream()
                .sorted(Comparator
                        .<Map<String, Object>>comparingInt(reference -> intValue(reference.get("referencePriorityScore")))
                        .reversed()
                        .thenComparing(reference -> text(reference.get("referenceId"))))
                .limit(LEGAL_REFERENCE_REVIEW_LIMIT)
                .map(Map::copyOf)
                .toList();
    }

    private boolean referenceMatchesCurrentReport(
            Map<String, Object> reference,
            Set<String> selectedChecklistItemCodes
    ) {
        var checklistItemCode = text(reference.get("checklistItemCode"));
        if (checklistItemCode.isBlank()) {
            return true;
        }
        return selectedChecklistItemCodes == null
                || selectedChecklistItemCodes.isEmpty()
                || selectedChecklistItemCodes.contains(checklistItemCode);
    }

    private Set<String> selectedChecklistItemCodes(InspectionReport report) {
        var dailyLog = stepRepository.findByReportIdAndStepCode(report.id(), "DAILY_LOG")
                .map(InspectionReportStep::payloadJson)
                .map(payload -> objectMap(payload.get("dailyItems")))
                .orElse(Map.of());
        if (dailyLog.isEmpty()) {
            return Set.of();
        }
        var codes = new java.util.LinkedHashSet<String>();
        for (var rawGroup : listValue(dailyLog.get("groups"))) {
            var group = objectMap(rawGroup);
            for (var rawEntry : listValue(group.get("entries"))) {
                var entry = objectMap(rawEntry);
                addCode(codes, entry.get("inspectionItemCode"));
                for (var rawRow : listValue(entry.get("checklistRows"))) {
                    var row = objectMap(rawRow);
                    addCode(codes, row.get("code"));
                    addCode(codes, row.get("checklistItemCode"));
                }
            }
        }
        return Set.copyOf(codes);
    }

    private void addCode(Set<String> codes, Object value) {
        var code = text(value);
        if (!code.isBlank()) {
            codes.add(code);
        }
    }

    private List<ReportPreflightReviewFinding> legalContextFindings(List<ReportPreflightReviewFinding> findings) {
        return findings.stream()
                .filter(this::isLegalContextFinding)
                .toList();
    }

    private boolean isLegalContextFinding(ReportPreflightReviewFinding finding) {
        if (finding == null) {
            return false;
        }
        var code = upper(finding.code());
        if (code.startsWith("LEGAL_") || "LEGAL_CONTEXT".equalsIgnoreCase(text(finding.location()))) {
            return true;
        }
        var attributes = finding.attributesJson();
        return !text(attributes.get("legalReferences")).isBlank()
                || !text(attributes.get("legalReferenceDetails")).isBlank();
    }

    private void mergeReference(
            LinkedHashMap<String, Map<String, Object>> byId,
            Map<String, Object> reference
    ) {
        var referenceId = text(reference.get("referenceId"));
        if (referenceId.isBlank()) {
            return;
        }
        byId.merge(referenceId, reference, this::preferredReference);
    }

    private Map<String, Object> preferredReference(
            Map<String, Object> left,
            Map<String, Object> right
    ) {
        var merged = new LinkedHashMap<String, Object>();
        var leftWins = intValue(left.get("referencePriorityScore")) >= intValue(right.get("referencePriorityScore"));
        var primary = leftWins ? left : right;
        var fallback = leftWins ? right : left;
        fallback.forEach((key, value) -> {
            if (!text(value).isBlank()) {
                merged.put(key, value);
            }
        });
        primary.forEach((key, value) -> {
            if (!text(value).isBlank()) {
                merged.put(key, value);
            }
        });
        return Map.copyOf(merged);
    }

    private Map<String, Object> enrichReference(
            Map<String, Object> reference,
            ReportPreflightReviewFinding finding
    ) {
        var enriched = new LinkedHashMap<String, Object>(reference);
        enriched.putIfAbsent("sourceFindingCode", finding.code());
        enriched.putIfAbsent("sourceFindingSeverity", finding.severity());
        enriched.putIfAbsent("sourceFindingLocation", finding.location() == null ? "" : finding.location());
        enriched.putIfAbsent("sourceFindingMessage", finding.message());
        enriched.put("anchorRole", legalReferenceAnchorRole(enriched));
        enriched.put("referencePriorityScore", referencePriorityScore(enriched, finding));
        return Map.copyOf(enriched);
    }

    private List<Map<String, Object>> parseLegalReferenceDetails(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(this::legalReferenceDetail)
                .filter(reference -> !text(reference.get("referenceId")).isBlank())
                .toList();
    }

    private Map<String, Object> legalReferenceDetail(String line) {
        var parts = line.split("\\t", -1);
        var reference = new LinkedHashMap<String, Object>();
        reference.put("referenceId", part(parts, 0));
        reference.put("label", part(parts, 1));
        reference.put("resolutionSource", part(parts, 2));
        reference.put("bindingScope", part(parts, 3));
        reference.put("bindingKey", part(parts, 4));
        reference.put("relevance", part(parts, 5));
        reference.put("catalogCode", part(parts, 6));
        reference.put("catalogVersion", part(parts, 7));
        reference.put("checklistItemCode", part(parts, 8));
        return Map.copyOf(reference);
    }

    private String legalReferenceAnchorRole(Map<String, Object> reference) {
        var resolutionSource = upper(reference.get("resolutionSource"));
        var bindingScope = upper(reference.get("bindingScope"));
        var relevance = upper(reference.get("relevance"));
        if ("CANDIDATE".equals(relevance) || "LEGAL_CORPUS_SEARCH".equals(bindingScope)) {
            return "SEARCH_CANDIDATE";
        }
        if ("LEGAL_DOMAIN_BINDING".equals(resolutionSource) && "CATALOG_ITEM".equals(bindingScope)) {
            return "BUSINESS_ITEM_ANCHOR";
        }
        if ("LEGAL_DOMAIN_BINDING".equals(resolutionSource) && "REPORT_TYPE".equals(bindingScope)) {
            return "REPORT_TYPE_ANCHOR";
        }
        if ("LEGAL_DOMAIN_BINDING".equals(resolutionSource)) {
            return "DOMAIN_ANCHOR";
        }
        return "REFERENCE_ANCHOR";
    }

    private int referencePriorityScore(
            Map<String, Object> reference,
            ReportPreflightReviewFinding finding
    ) {
        var score = switch (upper(reference.get("relevance"))) {
            case "PRIMARY" -> 500;
            case "SUPPORTING" -> 410;
            case "REFERENCE" -> 300;
            case "CANDIDATE" -> 150;
            default -> 100;
        };
        if ("LEGAL_DOMAIN_BINDING".equals(upper(reference.get("resolutionSource")))) {
            score += 120;
        }
        score += switch (upper(reference.get("bindingScope"))) {
            case "CATALOG_ITEM" -> 90;
            case "REPORT_TYPE" -> 60;
            case "LEGAL_CORPUS_SEARCH" -> 10;
            default -> 0;
        };
        if (!text(reference.get("checklistItemCode")).isBlank()) {
            score += 55;
        }
        if (!text(reference.get("catalogCode")).isBlank()) {
            score += 30;
        }
        score += switch (upper(finding.severity())) {
            case "CRITICAL" -> 40;
            case "HIGH" -> 30;
            case "MEDIUM" -> 15;
            default -> 0;
        };
        if (finding.code().contains("LEGAL")) {
            score += 10;
        }
        return score;
    }

    private LegalReferenceCoverage legalReferenceCoverage(List<Map<String, Object>> references) {
        return legalReferenceCoverage(references, Map.of());
    }

    private LegalReferenceCoverage legalReferenceCoverage(
            List<Map<String, Object>> references,
            LegalReviewContext context
    ) {
        var reportSnapshot = reportSnapshot(context.report());
        var steps = stepSnapshot(context.report());
        return legalReferenceCoverage(references, reportEvidenceChecklist(steps, reportSnapshot));
    }

    private LegalReferenceCoverage legalReferenceCoverage(
            List<Map<String, Object>> references,
            Map<String, Object> evidenceChecklist
    ) {
        var total = references.size();
        var primary = 0;
        var supporting = 0;
        var reference = 0;
        var candidate = 0;
        var domainBinding = 0;
        var catalogItem = 0;
        var reportType = 0;
        var corpusSearch = 0;
        var businessItemAnchor = 0;
        var reportTypeAnchor = 0;
        for (var item : references) {
            switch (upper(item.get("relevance"))) {
                case "PRIMARY" -> primary++;
                case "SUPPORTING" -> supporting++;
                case "REFERENCE" -> reference++;
                case "CANDIDATE" -> candidate++;
                default -> {
                }
            }
            if ("LEGAL_DOMAIN_BINDING".equals(upper(item.get("resolutionSource")))) {
                domainBinding++;
            }
            if ("CATALOG_ITEM".equals(upper(item.get("bindingScope")))) {
                catalogItem++;
            }
            if ("REPORT_TYPE".equals(upper(item.get("bindingScope")))) {
                reportType++;
            }
            if ("LEGAL_CORPUS_SEARCH".equals(upper(item.get("bindingScope")))) {
                corpusSearch++;
            }
            if ("BUSINESS_ITEM_ANCHOR".equals(upper(item.get("anchorRole")))) {
                businessItemAnchor++;
            }
            if ("REPORT_TYPE_ANCHOR".equals(upper(item.get("anchorRole")))) {
                reportTypeAnchor++;
            }
        }
        var grade = legalReferenceGrade(
                total,
                primary,
                supporting,
                candidate,
                domainBinding,
                catalogItem,
                reportType,
                corpusSearch,
                businessItemAnchor,
                reportTypeAnchor);
        var legalEligible = grade == LegalReferenceGrade.A || grade == LegalReferenceGrade.B;
        var evidenceEligible = evidenceEligible(evidenceChecklist);
        var technicalCriteriaEligible = technicalCriteriaEligible(evidenceChecklist);
        var applicabilityEligible = grade == LegalReferenceGrade.A && primary > 0 && businessItemAnchor > 0;
        var blockers = passBlockers(
                total,
                primary,
                candidate,
                domainBinding,
                corpusSearch,
                businessItemAnchor,
                reportTypeAnchor,
                grade,
                evidenceChecklist,
                legalEligible,
                evidenceEligible,
                technicalCriteriaEligible,
                applicabilityEligible);
        var eligibility = new PassEligibility(
                legalEligible,
                evidenceEligible,
                technicalCriteriaEligible,
                applicabilityEligible,
                legalEligible && evidenceEligible && applicabilityEligible && blockers.isEmpty(),
                blockers);
        var passEligible = eligibility.finalEligible();
        var strength = eligibility.finalEligible() && technicalCriteriaEligible
                ? "HIGH"
                : legalEligible && evidenceEligible
                ? "MEDIUM"
                : total == 0
                ? "NONE"
                : "LOW";
        var limitations = blockers.stream()
                .map(PassBlocker::message)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        if (total >= LEGAL_REFERENCE_REVIEW_LIMIT) {
            limitations.add("근거 후보가 많아 우선순위 상위 근거만 AI 검토에 사용했습니다.");
        }
        if (!technicalCriteriaEligible) {
            limitations.add("성능·규격 등 실질 기술기준 적합성은 설계도서, 시방서, 시험성적서, 승인서 등 별도 근거 문서가 연결되지 않아 검토 범위에서 제외했습니다.");
        }
        return new LegalReferenceCoverage(
                total,
                primary,
                supporting,
                reference,
                candidate,
                domainBinding,
                catalogItem,
                reportType,
                corpusSearch,
                businessItemAnchor,
                reportTypeAnchor,
                passEligible,
                strength,
                grade,
                eligibility,
                List.copyOf(limitations));
    }

    private LegalReferenceGrade legalReferenceGrade(
            int total,
            int primary,
            int supporting,
            int candidate,
            int domainBinding,
            int catalogItem,
            int reportType,
            int corpusSearch,
            int businessItemAnchor,
            int reportTypeAnchor
    ) {
        if (total == 0) {
            return LegalReferenceGrade.X;
        }
        if (domainBinding > 0
                && catalogItem > 0
                && businessItemAnchor > 0
                && primary > 0) {
            return LegalReferenceGrade.A;
        }
        if (domainBinding > 0
                && (catalogItem > 0 || reportType > 0)
                && (supporting > 0 || reportTypeAnchor > 0 || businessItemAnchor > 0)) {
            return LegalReferenceGrade.B;
        }
        if (domainBinding == 0 && corpusSearch > 0 && candidate > 0) {
            return LegalReferenceGrade.C;
        }
        return candidate > 0 || primary > 0 || supporting > 0
                ? LegalReferenceGrade.D
                : LegalReferenceGrade.X;
    }

    private boolean evidenceEligible(Map<String, Object> evidenceChecklist) {
        if (evidenceChecklist == null || evidenceChecklist.isEmpty()) {
            return true;
        }
        if (!"CONSTRUCTION_DAILY_SUPERVISION_LOG".equals(upper(evidenceChecklist.get("reportType")))) {
            return true;
        }
        return intValue(evidenceChecklist.get("dailyLogEntryCount")) > 0
                && intValue(evidenceChecklist.get("dailyLogEntriesWithSupervisionContent")) > 0
                && intValue(evidenceChecklist.get("dailyLogEntriesWithChecklistItemCode")) > 0
                && intValue(evidenceChecklist.get("dailyLogEntriesWithPhotoIds")) > 0
                && Boolean.TRUE.equals(evidenceChecklist.get("allDailyLogPhotoRefsResolved"))
                && !Boolean.TRUE.equals(evidenceChecklist.get("generationBlockingPhotoIssue"));
    }

    private boolean technicalCriteriaEligible(Map<String, Object> evidenceChecklist) {
        if (evidenceChecklist == null || evidenceChecklist.isEmpty()) {
            return true;
        }
        if (!"CONSTRUCTION_DAILY_SUPERVISION_LOG".equals(upper(evidenceChecklist.get("reportType")))) {
            return true;
        }
        if (!Boolean.TRUE.equals(evidenceChecklist.get("technicalCriteriaReviewRequired"))) {
            return true;
        }
        return intValue(evidenceChecklist.get("dailyLogEntriesRequiringTechnicalCriteria")) > 0
                && intValue(evidenceChecklist.get("dailyLogEntriesWithTechnicalCriteriaEvidence"))
                >= intValue(evidenceChecklist.get("dailyLogEntriesRequiringTechnicalCriteria"));
    }

    private List<PassBlocker> passBlockers(
            int total,
            int primary,
            int candidate,
            int domainBinding,
            int corpusSearch,
            int businessItemAnchor,
            int reportTypeAnchor,
            LegalReferenceGrade grade,
            Map<String, Object> evidenceChecklist,
            boolean legalEligible,
            boolean evidenceEligible,
            boolean technicalCriteriaEligible,
            boolean applicabilityEligible
    ) {
        var blockers = new ArrayList<PassBlocker>();
        if (total == 0 || grade == LegalReferenceGrade.X) {
            blockers.add(blocker(
                    "PASS_BLOCKED_NO_LEGAL_REFERENCE",
                    "LEGAL",
                    "PASS 판정에 사용할 업무-법령 근거가 없습니다."));
        }
        if (!legalEligible) {
            if (domainBinding == 0 && corpusSearch > 0 && candidate > 0) {
                blockers.add(blocker(
                        "PASS_BLOCKED_SEARCH_CANDIDATE_ONLY",
                        "LEGAL",
                        "법령 검색 후보만으로는 PASS 판정을 허용하지 않습니다."));
            } else if (primary == 0) {
                blockers.add(blocker(
                        "PASS_BLOCKED_NO_PRIMARY_REFERENCE",
                        "LEGAL",
                        "PASS 판정에는 주요 근거 조문이 필요합니다."));
            } else {
                blockers.add(blocker(
                        "PASS_BLOCKED_LEGAL_REFERENCE_TOO_WEAK",
                        "LEGAL",
                        "업무-법령 근거 강도가 PASS 판정에 충분하지 않습니다."));
            }
        }
        if (reportTypeAnchor > 0 && businessItemAnchor == 0) {
            blockers.add(blocker(
                    "PASS_BLOCKED_REPORT_TYPE_ANCHOR_ONLY",
                    "APPLICABILITY",
                    "리포트 유형 근거만으로는 개별 검사항목의 법령검토 PASS를 허용하지 않습니다."));
        }
        if (!applicabilityEligible && businessItemAnchor == 0) {
            blockers.add(blocker(
                    "PASS_BLOCKED_NO_BUSINESS_ITEM_ANCHOR",
                    "APPLICABILITY",
                    "체크리스트 항목 단위 업무-법령 바인딩 근거가 부족합니다."));
        }
        if (!evidenceEligible) {
            blockers.addAll(evidenceBlockers(evidenceChecklist));
        }
        return List.copyOf(blockers);
    }

    private List<PassBlocker> evidenceBlockers(Map<String, Object> evidenceChecklist) {
        if (evidenceChecklist == null || evidenceChecklist.isEmpty()) {
            return List.of();
        }
        if (!"CONSTRUCTION_DAILY_SUPERVISION_LOG".equals(upper(evidenceChecklist.get("reportType")))) {
            return List.of();
        }
        var blockers = new ArrayList<PassBlocker>();
        if (intValue(evidenceChecklist.get("dailyLogEntryCount")) <= 0) {
            blockers.add(blocker("PASS_BLOCKED_NO_DAILY_LOG_ENTRY", "EVIDENCE", "감리일지 검토 항목이 없습니다."));
        }
        if (intValue(evidenceChecklist.get("dailyLogEntriesWithSupervisionContent")) <= 0) {
            blockers.add(blocker("PASS_BLOCKED_MISSING_SUPERVISION_CONTENT", "EVIDENCE", "감리내용이 있는 검토 항목이 없습니다."));
        }
        if (intValue(evidenceChecklist.get("dailyLogEntriesWithChecklistItemCode")) <= 0) {
            blockers.add(blocker("PASS_BLOCKED_MISSING_CHECKLIST_ITEM", "EVIDENCE", "검사항목 코드가 연결된 감리 항목이 없습니다."));
        }
        if (intValue(evidenceChecklist.get("dailyLogEntriesWithPhotoIds")) <= 0) {
            blockers.add(blocker("PASS_BLOCKED_MISSING_PHOTO_EVIDENCE", "EVIDENCE", "검토 항목에 연결된 사진 증거가 없습니다."));
        }
        if (!Boolean.TRUE.equals(evidenceChecklist.get("allDailyLogPhotoRefsResolved"))) {
            blockers.add(blocker("PASS_BLOCKED_UNRESOLVED_PHOTO_REFERENCE", "EVIDENCE", "감리 항목의 사진 참조가 업로드 사진과 일치하지 않습니다."));
        }
        if (Boolean.TRUE.equals(evidenceChecklist.get("generationBlockingPhotoIssue"))) {
            blockers.add(blocker("PASS_BLOCKED_GENERATION_BLOCKING_PHOTO_ISSUE", "EVIDENCE", "문서 생성을 막는 사진 증거 문제가 있습니다."));
        }
        return List.copyOf(blockers);
    }

    private PassBlocker blocker(String code, String category, String message) {
        return new PassBlocker(code, category, message);
    }

    private Map<String, Object> reportEvidenceChecklist(
            Map<String, Object> steps,
            Map<String, Object> reportSnapshot
    ) {
        var checklist = new LinkedHashMap<String, Object>();
        var dailyLogStep = objectMap(steps.get("DAILY_LOG"));
        var dailyLogPayload = objectMap(dailyLogStep.get("payload"));
        var dailyItems = objectMap(dailyLogPayload.get("dailyItems"));
        var groups = listValue(dailyItems.get("groups"));
        var entryCount = 0;
        var entriesWithSupervisionContent = 0;
        var entriesWithPhotoIds = 0;
        var entriesWithChecklistItemCode = 0;
        var entriesRequiringTechnicalCriteria = 0;
        var entriesWithTechnicalCriteriaEvidence = 0;
        for (var groupValue : groups) {
            var group = objectMap(groupValue);
            for (var entryValue : listValue(group.get("entries"))) {
                var entry = objectMap(entryValue);
                entryCount++;
                if (!DailySupervisionContentFormatter.formatEntry(entry).isBlank()) {
                    entriesWithSupervisionContent++;
                }
                if (!dailyEntryPhotoIds(entry).isEmpty()) {
                    entriesWithPhotoIds++;
                }
                if (!text(entry.get("inspectionItemCode")).isBlank()
                        || !text(entry.get("checklistItemCode")).isBlank()) {
                    entriesWithChecklistItemCode++;
                }
                if (requiresTechnicalCriteriaReview(group, entry)) {
                    entriesRequiringTechnicalCriteria++;
                    if (hasTechnicalCriteriaEvidence(group, entry)) {
                        entriesWithTechnicalCriteriaEvidence++;
                    }
                }
            }
        }
        var remarksStep = objectMap(steps.get("REMARKS"));
        var remarksPayload = objectMap(remarksStep.get("payload"));
        var photoEvidenceStatus = objectMap(reportSnapshot.get("photoEvidenceStatus"));
        checklist.put("reportType", text(reportSnapshot.get("reportType")));
        checklist.put("dailyLogGroupCount", groups.size());
        checklist.put("dailyLogEntryCount", entryCount);
        checklist.put("dailyLogEntriesWithSupervisionContent", entriesWithSupervisionContent);
        checklist.put("dailyLogEntriesWithPhotoIds", entriesWithPhotoIds);
        checklist.put("dailyLogEntriesWithChecklistItemCode", entriesWithChecklistItemCode);
        checklist.put("technicalCriteriaReviewRequired", entriesRequiringTechnicalCriteria > 0);
        checklist.put("dailyLogEntriesRequiringTechnicalCriteria", entriesRequiringTechnicalCriteria);
        checklist.put("dailyLogEntriesWithTechnicalCriteriaEvidence", entriesWithTechnicalCriteriaEvidence);
        checklist.put("hasIssueAndAction", !text(dailyLogPayload.get("issueAndAction")).isBlank()
                || !text(remarksPayload.get("issueAndAction")).isBlank());
        checklist.put("hasNextAction", !text(dailyLogPayload.get("nextAction")).isBlank()
                || !text(remarksPayload.get("nextAction")).isBlank());
        checklist.put("allDailyLogPhotoRefsResolved", Boolean.TRUE.equals(photoEvidenceStatus.get("allDailyLogPhotoRefsResolved")));
        checklist.put("generationBlockingPhotoIssue", Boolean.TRUE.equals(photoEvidenceStatus.get("generationBlockingPhotoIssue")));
        checklist.put("photoEvidenceSource", text(photoEvidenceStatus.get("photoSourceOfTruth")));
        return Map.copyOf(checklist);
    }

    private List<?> dailyEntryPhotoIds(Map<String, Object> entry) {
        var photoIds = new ArrayList<Object>();
        for (Object rowValue : listValue(entry.get("checklistRows"))) {
            photoIds.addAll(listValue(objectMap(rowValue).get("photoIds")));
        }
        return photoIds;
    }

    private boolean requiresTechnicalCriteriaReview(Map<String, Object> group, Map<String, Object> entry) {
        var value = String.join(" ",
                text(group.get("tradeName")),
                text(group.get("phaseName")),
                text(group.get("processName")),
                text(entry.get("inspectionItemName")),
                text(entry.get("inspectionItemCode")),
                text(entry.get("checklistItemCode")),
                DailySupervisionContentFormatter.formatEntry(entry));
        return containsAny(value,
                "자재", "재료", "성능", "규격", "품질", "강도", "두께", "치수", "단열", "기밀",
                "수밀", "내풍압", "방화", "차음", "시험", "인증", "설비", "배근", "철근",
                "콘크리트", "창호", "접합", "부호");
    }

    private boolean hasTechnicalCriteriaEvidence(Map<String, Object> group, Map<String, Object> entry) {
        var value = String.join(" ",
                text(group.get("tradeName")),
                text(group.get("phaseName")),
                text(group.get("processName")),
                text(entry.get("inspectionItemName")),
                DailySupervisionContentFormatter.formatEntry(entry));
        return containsAny(value,
                "설계도서", "설계 도서", "도면", "시방서", "시험성적서", "성적서", "납품승인",
                "자재승인", "승인서", "인증서", "KS", "성능시험", "품질시험", "검사서",
                "확인서", "승인 자재", "반입 자재", "제조사", "모델명", "규격 확인",
                "도면과 일치", "시방서와 일치", "승인서와 일치");
    }

    private boolean containsAny(String value, String... needles) {
        var normalized = text(value).replace(" ", "").toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return false;
        }
        for (var needle : needles) {
            if (normalized.contains(text(needle).replace(" ", "").toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private Optional<SourceBackedLegalReviewResult> result(Optional<ValidationResult<?>> validation) {
        return validation
                .filter(ValidationResult::isValid)
                .flatMap(value -> {
                    if (value instanceof ValidationResult.Valid<?> valid
                            && valid.value() instanceof SourceBackedLegalReviewResult result) {
                        return Optional.of(result);
                    }
                    return Optional.empty();
                });
    }

    private void saveSingleFinding(LegalReviewContext context, ReportPreflightReviewFinding finding) {
        transactionTemplate.executeWithoutResult(status -> {
            findingRepository.deleteByReviewRunIdAndSource(context.run().id(), SOURCE);
            ReportPreflightFindingClassifier.autoResolveOnCreate(finding, context.request().requestedBy(), OffsetDateTime.now());
            findingRepository.save(finding);
        });
    }

    private void saveResult(
            LegalReviewContext context,
            List<Map<String, Object>> references,
            LegalReferenceCoverage coverage,
            SourceBackedLegalReviewResult result,
            String providerCode,
            String modelId,
            String harnessRunId
    ) {
        var referencesById = referencesById(references);
        var reviewedIds = safeReferenceIds(result.reviewedReferenceIds(), referencesById);
        var status = result.status();
        if (status == SourceBackedLegalReviewStatus.PASS && reviewedIds.isEmpty()) {
            saveSingleFinding(context, insufficientContextFinding(context, references));
            return;
        }
        if (status == SourceBackedLegalReviewStatus.PASS && !coverage.passEligibleForPass()) {
            saveSingleFinding(context, insufficientContextFinding(context, references, String.join(" ", coverage.limitations())));
            return;
        }
        var findings = new java.util.ArrayList<ReportPreflightReviewFinding>();
        findings.add(summaryFinding(context, referencesById, coverage, result, reviewedIds, providerCode, modelId, harnessRunId));
        var aiIssuePaths = result.issues().stream()
                .map(SourceBackedLegalReviewIssue::relatedFieldPath)
                .map(this::text)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
        findings.addAll(dailyLogDocumentCompletionFindings(
                context,
                references,
                referencesById,
                aiIssuePaths,
                result,
                providerCode,
                modelId,
                harnessRunId));
        for (var issue : result.issues()) {
            findings.add(issueFinding(context, referencesById, result, issue, providerCode, modelId, harnessRunId));
        }
        transactionTemplate.executeWithoutResult(tx -> {
            findingRepository.deleteByReviewRunIdAndSource(context.run().id(), SOURCE);
            var now = OffsetDateTime.now();
            findings.forEach(finding -> ReportPreflightFindingClassifier.autoResolveOnCreate(
                    finding,
                    context.request().requestedBy(),
                    now));
            findingRepository.saveAll(findings);
        });
    }

    private ReportPreflightReviewFinding summaryFinding(
            LegalReviewContext context,
            Map<String, Map<String, Object>> referencesById,
            LegalReferenceCoverage coverage,
            SourceBackedLegalReviewResult result,
            List<String> reviewedIds,
            String providerCode,
            String modelId,
            String harnessRunId
    ) {
        var attributes = baseAttributes(result.status(), result.confidence().name(), providerCode, modelId, harnessRunId);
        attributes.put("category", "LEGAL_REVIEW");
        attributes.put("approvalRequired", result.status() == SourceBackedLegalReviewStatus.INSUFFICIENT_CONTEXT ? "true" : "false");
        attributes.put("legalReviewScope", result.legalReviewScope());
        attributes.put("passReason", result.passReason());
        attributes.put("limitations", result.limitations());
        attributes.put("reviewedReferenceCount", String.valueOf(reviewedIds.size()));
        addCoverageAttributes(attributes, coverage);
        if (!reviewedIds.isEmpty()) {
            attributes.put("legalReferences", String.join(",", reviewedIds));
            attributes.put("legalReferenceDetails", referenceDetails(reviewedIds, referencesById));
        }
        return new ReportPreflightReviewFinding(
                context.report().officeId(),
                context.run().id(),
                context.report().id(),
                SOURCE,
                summaryCode(result.status()),
                summarySeverity(result.status()),
                "LEGAL_REVIEW",
                summaryMessage(result),
                summaryEvidence(reviewedIds),
                Map.copyOf(attributes),
                OffsetDateTime.now());
    }

    private ReportPreflightReviewFinding issueFinding(
            LegalReviewContext context,
            Map<String, Map<String, Object>> referencesById,
            SourceBackedLegalReviewResult result,
            SourceBackedLegalReviewIssue issue,
            String providerCode,
            String modelId,
            String harnessRunId
    ) {
        var referenceIds = safeReferenceIds(issue.legalReferenceIds(), referencesById);
        var attributes = baseAttributes(result.status(), result.confidence().name(), providerCode, modelId, harnessRunId);
        attributes.put("category", issue.category().name());
        attributes.put("approvalRequired", String.valueOf(legalIssueApprovalRequired(issue)));
        attributes.put("suggestion", issue.suggestion());
        if (!issue.replacement().isBlank()) {
            attributes.put("replacement", issue.replacement());
        }
        if (!issue.relatedFieldPath().isBlank()) {
            attributes.put("relatedFieldPath", issue.relatedFieldPath());
        }
        if (!referenceIds.isEmpty()) {
            attributes.put("legalReferences", String.join(",", referenceIds));
            attributes.put("legalReferenceDetails", referenceDetails(referenceIds, referencesById));
        }
        return new ReportPreflightReviewFinding(
                context.report().officeId(),
                context.run().id(),
                context.report().id(),
                SOURCE,
                issue.code(),
                issue.severity().name(),
                issue.location(),
                issue.message(),
                issue.evidence(),
                Map.copyOf(attributes),
                OffsetDateTime.now());
    }

    private List<ReportPreflightReviewFinding> dailyLogDocumentCompletionFindings(
            LegalReviewContext context,
            List<Map<String, Object>> references,
            Map<String, Map<String, Object>> referencesById,
            Set<String> aiIssuePaths,
            SourceBackedLegalReviewResult result,
            String providerCode,
            String modelId,
            String harnessRunId
    ) {
        if (!"CONSTRUCTION_DAILY_SUPERVISION_LOG".equals(context.report().reportType())) {
            return List.of();
        }
        var steps = stepSnapshot(context.report());
        var dailyLogStep = objectMap(steps.get("DAILY_LOG"));
        var dailyLogPayload = objectMap(dailyLogStep.get("payload"));
        var dailyItems = objectMap(dailyLogPayload.get("dailyItems"));
        var groups = listValue(dailyItems.get("groups"));
        if (groups.isEmpty()) {
            return List.of();
        }

        var findings = new ArrayList<ReportPreflightReviewFinding>();
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            var group = objectMap(groups.get(groupIndex));
            var entries = listValue(group.get("entries"));
            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                var entry = objectMap(entries.get(entryIndex));
                if (!requiresDocumentCompletionWording(group, entry)
                        || hasDocumentCompletionWording(group, entry)) {
                    continue;
                }
                var targetRowIndex = firstChecklistRowIndex(entry);
                if (targetRowIndex == null) {
                    continue;
                }
                var relatedFieldPath = "DAILY_LOG.groups[" + groupIndex + "].entries[" + entryIndex
                        + "].checklistRows[" + targetRowIndex + "].referenceNote";
                if (aiIssuePaths.contains(relatedFieldPath)) {
                    continue;
                }
                findings.add(dailyLogDocumentCompletionFinding(
                        context,
                        references,
                        referencesById,
                        group,
                        entry,
                        relatedFieldPath,
                        result,
                        providerCode,
                        modelId,
                        harnessRunId));
                if (findings.size() >= 8) {
                    return List.copyOf(findings);
                }
            }
        }
        return List.copyOf(findings);
    }

    private Integer firstChecklistRowIndex(Map<String, Object> entry) {
        var rows = listValue(entry.get("checklistRows"));
        return rows.isEmpty() ? null : 0;
    }

    private ReportPreflightReviewFinding dailyLogDocumentCompletionFinding(
            LegalReviewContext context,
            List<Map<String, Object>> references,
            Map<String, Map<String, Object>> referencesById,
            Map<String, Object> group,
            Map<String, Object> entry,
            String relatedFieldPath,
            SourceBackedLegalReviewResult result,
            String providerCode,
            String modelId,
            String harnessRunId
    ) {
        var referenceIds = matchingReferenceIds(references, group, entry);
        var attributes = baseAttributes(result.status(), result.confidence().name(), providerCode, modelId, harnessRunId);
        attributes.put("category", "COMPLIANCE");
        attributes.put("approvalRequired", "true");
        attributes.put("suggestion", "수정안을 적용하면 해당 감리 항목에 필요한 서류 확인 및 첨부 문구가 감리일지에 반영됩니다.");
        attributes.put("replacement", documentCompletionReplacement(group, entry));
        attributes.put("relatedFieldPath", relatedFieldPath);
        attributes.put("documentCompletionDraft", "true");
        if (!referenceIds.isEmpty()) {
            attributes.put("legalReferences", String.join(",", referenceIds));
            attributes.put("legalReferenceDetails", referenceDetails(referenceIds, referencesById));
        }
        return new ReportPreflightReviewFinding(
                context.report().officeId(),
                context.run().id(),
                context.report().id(),
                SOURCE,
                "DAILY_LOG_DOCUMENT_CONFIRMATION_WORDING_REQUIRED",
                "MEDIUM",
                relatedFieldPath,
                "감리일지에 필요한 서류 확인 및 첨부 문구가 부족합니다.",
                "inspectionItem=" + firstNonBlank(text(entry.get("inspectionItemName")), text(entry.get("inspectionItemCode")))
                        + "; supervisionContent=" + DailySupervisionContentFormatter.formatEntry(entry),
                Map.copyOf(attributes),
                OffsetDateTime.now());
    }

    private boolean requiresDocumentCompletionWording(Map<String, Object> group, Map<String, Object> entry) {
        var value = String.join(" ",
                text(group.get("tradeName")),
                text(group.get("phaseName")),
                text(group.get("processName")),
                text(entry.get("inspectionItemName")),
                text(entry.get("inspectionItemCode")),
                text(entry.get("checklistItemCode")),
                DailySupervisionContentFormatter.formatEntry(entry));
        return containsAny(value,
                "자재", "재료", "성능", "규격", "품질", "시험", "인증", "검사필증", "필증",
                "KS", "성적서", "승인서", "증명서", "시방서", "단열", "기밀", "수밀", "내풍압",
                "방화", "차음", "제조업체");
    }

    private boolean hasDocumentCompletionWording(Map<String, Object> group, Map<String, Object> entry) {
        var value = String.join(" ",
                text(group.get("tradeName")),
                text(group.get("phaseName")),
                text(group.get("processName")),
                text(entry.get("inspectionItemName")),
                DailySupervisionContentFormatter.formatEntry(entry));
        return containsAny(value, "첨부", "제출");
    }

    private String documentCompletionReplacement(Map<String, Object> group, Map<String, Object> entry) {
        var value = String.join(" ",
                text(group.get("tradeName")),
                text(group.get("phaseName")),
                text(group.get("processName")),
                text(entry.get("inspectionItemName")),
                text(entry.get("inspectionItemCode")),
                DailySupervisionContentFormatter.formatEntry(entry));
        if (containsAny(value, "창호", "창 및 문", "창및문")) {
            return "창호 자재의 단열·기밀·수밀·내풍압 등 성능 항목을 관련 기준 및 설계도서에 따라 확인하였으며, 시방서·시험성적서·자재승인서 등 관련 서류를 확인하고 첨부하였습니다.";
        }
        if (containsAny(value, "단열", "단열재")) {
            return "단열재의 규격, 두께 및 성능 항목을 관련 기준 및 설계도서에 따라 확인하였으며, 시방서·시험성적서·자재승인서 등 관련 서류를 확인하고 첨부하였습니다.";
        }
        if (containsAny(value, "전기안전검사필증", "검사필증", "필증")) {
            return "전기안전검사필증의 발급기관, 발급일, 대상 설비 및 현장 위치를 확인하였으며, 관련 필증을 확인하고 첨부하였습니다.";
        }
        if (containsAny(value, "철근", "규격 증명서", "규격증명서", "제조업체", "재료시험")) {
            return "철근 규격 증명서, 제조업체, 재료시험 필요 여부 및 KS 등 자재성능 관련 서류를 확인하고, 관련 증빙 서류를 첨부하였습니다.";
        }
        var itemName = firstNonBlank(text(entry.get("inspectionItemName")), text(entry.get("checklistItemCode")));
        if (itemName.isBlank()) {
            itemName = firstNonBlank(text(group.get("tradeName")), text(group.get("phaseName")), "해당 감리 항목");
        }
        return itemName + "에 대하여 관련 기준 및 설계도서 기준에 따라 확인하였으며, 시방서·시험성적서·자재승인서·인증서 등 관련 서류를 확인하고 첨부하였습니다.";
    }

    private List<String> matchingReferenceIds(
            List<Map<String, Object>> references,
            Map<String, Object> group,
            Map<String, Object> entry
    ) {
        var tradeCode = text(group.get("tradeCode"));
        var phaseCode = text(group.get("phaseCode"));
        var processCode = text(group.get("processCode"));
        var itemCode = firstNonBlank(text(entry.get("inspectionItemCode")), text(entry.get("checklistItemCode")));
        var matched = references.stream()
                .filter(reference -> text(reference.get("checklistItemCode")).equals(itemCode)
                        || text(reference.get("bindingKey")).contains(itemCode)
                        || (!tradeCode.isBlank() && text(reference.get("bindingKey")).contains(tradeCode))
                        || (!phaseCode.isBlank() && text(reference.get("bindingKey")).contains(phaseCode))
                        || (!processCode.isBlank() && text(reference.get("bindingKey")).contains(processCode)))
                .map(reference -> text(reference.get("referenceId")))
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(3)
                .toList();
        if (!matched.isEmpty()) {
            return matched;
        }
        return references.stream()
                .map(reference -> text(reference.get("referenceId")))
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(3)
                .toList();
    }

    private boolean legalIssueApprovalRequired(SourceBackedLegalReviewIssue issue) {
        if (isTechnicalCriteriaScopeLimitation(issue)) {
            return false;
        }
        return switch (issue.severity()) {
            case HIGH, CRITICAL -> true;
            case INFO, LOW -> false;
            case MEDIUM -> issue.category() == com.archdox.legalai.SourceBackedLegalReviewIssueCategory.COMPLIANCE
                    || issue.category() == com.archdox.legalai.SourceBackedLegalReviewIssueCategory.LEGAL_RISK;
        };
    }

    private boolean isTechnicalCriteriaScopeLimitation(SourceBackedLegalReviewIssue issue) {
        var value = String.join(" ",
                issue.code(),
                issue.message(),
                issue.evidence(),
                issue.suggestion(),
                issue.location());
        return containsAny(value, "TECHNICAL_CRITERIA", "TECHNICAL_STANDARD", "기술기준", "실질기준", "성능·규격");
    }

    private ReportPreflightReviewFinding insufficientContextFinding(
            LegalReviewContext context,
            List<Map<String, Object>> references
    ) {
        return insufficientContextFinding(context, references, "법령검토에 사용할 업무-법령 근거가 없거나 충분하지 않습니다.");
    }

    private ReportPreflightReviewFinding insufficientContextFinding(
            LegalReviewContext context,
            List<Map<String, Object>> references,
            String limitations
    ) {
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("source", SOURCE);
        attributes.put("category", "LEGAL_REVIEW");
        attributes.put("legalReviewStatus", SourceBackedLegalReviewStatus.INSUFFICIENT_CONTEXT.name());
        attributes.put("reviewMode", REVIEW_MODE);
        attributes.put("draftOnly", "true");
        attributes.put("approvalRequired", "true");
        attributes.put("limitations", text(limitations).isBlank()
                ? "법령검토에 사용할 업무-법령 근거가 없거나 충분하지 않습니다."
                : text(limitations));
        addCoverageAttributes(attributes, legalReferenceCoverage(references));
        var ids = references.stream().map(reference -> text(reference.get("referenceId"))).filter(value -> !value.isBlank()).toList();
        if (!ids.isEmpty()) {
            attributes.put("legalReferences", String.join(",", ids));
            attributes.put("legalReferenceDetails", referenceDetails(ids, referencesById(references)));
        }
        return new ReportPreflightReviewFinding(
                context.report().officeId(),
                context.run().id(),
                context.report().id(),
                SOURCE,
                "LEGAL_REVIEW_INSUFFICIENT_CONTEXT",
                "MEDIUM",
                "LEGAL_REVIEW",
                "법령 근거 기반 검토에 필요한 근거가 충분하지 않습니다.",
                "legalReferenceCount=" + ids.size(),
                Map.copyOf(attributes),
                OffsetDateTime.now());
    }

    private void addCoverageAttributes(
            LinkedHashMap<String, String> attributes,
            LegalReferenceCoverage coverage
    ) {
        attributes.put("referenceCoverageStrength", coverage.reviewStrength());
        attributes.put("legalReferenceGrade", coverage.legalReferenceGrade().name());
        attributes.put("passEligibleForPass", String.valueOf(coverage.passEligibleForPass()));
        attributes.put("legalPassEligible", String.valueOf(coverage.passEligibility().legalEligible()));
        attributes.put("evidencePassEligible", String.valueOf(coverage.passEligibility().evidenceEligible()));
        attributes.put("technicalCriteriaPassEligible", String.valueOf(coverage.passEligibility().technicalCriteriaEligible()));
        attributes.put("applicabilityPassEligible", String.valueOf(coverage.passEligibility().applicabilityEligible()));
        attributes.put("finalPassEligible", String.valueOf(coverage.passEligibility().finalEligible()));
        attributes.put("primaryReferenceCount", String.valueOf(coverage.primaryCount()));
        attributes.put("supportingReferenceCount", String.valueOf(coverage.supportingCount()));
        attributes.put("candidateReferenceCount", String.valueOf(coverage.candidateCount()));
        attributes.put("businessItemAnchorCount", String.valueOf(coverage.businessItemAnchorCount()));
        attributes.put("reportTypeAnchorCount", String.valueOf(coverage.reportTypeAnchorCount()));
        if (!coverage.passEligibility().blockers().isEmpty()) {
            attributes.put(
                    "passBlockerCodes",
                    coverage.passEligibility().blockers().stream()
                            .map(PassBlocker::code)
                            .distinct()
                            .collect(Collectors.joining(",")));
        }
        if (!coverage.limitations().isEmpty()) {
            attributes.put("referenceCoverageLimitations", String.join(" ", coverage.limitations()));
        }
    }

    private ReportPreflightReviewFinding skippedFinding(
            LegalReviewContext context,
            List<Map<String, Object>> references,
            String reason
    ) {
        var ids = references.stream().map(reference -> text(reference.get("referenceId"))).filter(value -> !value.isBlank()).toList();
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("source", SOURCE);
        attributes.put("category", "LEGAL_REVIEW");
        attributes.put("legalReviewStatus", "SKIPPED");
        attributes.put("reviewMode", REVIEW_MODE);
        attributes.put("draftOnly", "true");
        attributes.put("approvalRequired", "false");
        attributes.put("skipReason", text(reason));
        if (!ids.isEmpty()) {
            attributes.put("legalReferences", String.join(",", ids));
            attributes.put("legalReferenceDetails", referenceDetails(ids, referencesById(references)));
        }
        return new ReportPreflightReviewFinding(
                context.report().officeId(),
                context.run().id(),
                context.report().id(),
                SOURCE,
                "LEGAL_REVIEW_SKIPPED",
                "INFO",
                "LEGAL_REVIEW",
                "법령검토 AI를 실행하지 못해 전용 법령검토를 생략했습니다.",
                text(reason),
                Map.copyOf(attributes),
                OffsetDateTime.now());
    }

    private ReportPreflightReviewFinding failedFinding(
            LegalReviewContext context,
            List<Map<String, Object>> references,
            String code,
            String message
    ) {
        var ids = references.stream().map(reference -> text(reference.get("referenceId"))).filter(value -> !value.isBlank()).toList();
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("source", SOURCE);
        attributes.put("category", "LEGAL_REVIEW");
        attributes.put("legalReviewStatus", "FAILED");
        attributes.put("reviewMode", REVIEW_MODE);
        attributes.put("draftOnly", "true");
        attributes.put("approvalRequired", "true");
        if (!ids.isEmpty()) {
            attributes.put("legalReferences", String.join(",", ids));
            attributes.put("legalReferenceDetails", referenceDetails(ids, referencesById(references)));
        }
        return new ReportPreflightReviewFinding(
                context.report().officeId(),
                context.run().id(),
                context.report().id(),
                SOURCE,
                code,
                "MEDIUM",
                "LEGAL_REVIEW",
                message,
                "source-backed legal review harness failed",
                Map.copyOf(attributes),
                OffsetDateTime.now());
    }

    private String userFacingAiFailureReason(String terminalReason) {
        var reason = text(terminalReason);
        if (reason.toLowerCase(Locale.ROOT).contains("validation failed")) {
            return "법령검토 AI 응답 형식을 확인하지 못해 이번 법령검토를 생략했습니다. 다시 실행하면 재시도됩니다.";
        }
        if (reason.toLowerCase(Locale.ROOT).contains("timeout")) {
            return "법령검토 AI 응답 시간이 초과되어 이번 법령검토를 생략했습니다. 다시 실행하면 재시도됩니다.";
        }
        return "법령검토 AI가 정상 완료되지 않아 이번 법령검토를 생략했습니다. 다시 실행하면 재시도됩니다.";
    }

    private LinkedHashMap<String, String> baseAttributes(
            SourceBackedLegalReviewStatus status,
            String confidence,
            String providerCode,
            String modelId,
            String harnessRunId
    ) {
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("source", SOURCE);
        attributes.put("legalReviewStatus", status.name());
        attributes.put("confidence", confidence);
        attributes.put("reviewMode", REVIEW_MODE);
        attributes.put("draftOnly", "true");
        attributes.put("providerCode", providerCode);
        attributes.put("modelId", modelId);
        attributes.put("harnessRunId", harnessRunId);
        return attributes;
    }

    private Map<String, Map<String, Object>> referencesById(List<Map<String, Object>> references) {
        return references.stream()
                .filter(reference -> !text(reference.get("referenceId")).isBlank())
                .collect(Collectors.toMap(
                        reference -> text(reference.get("referenceId")),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private List<String> safeReferenceIds(
            List<String> referenceIds,
            Map<String, Map<String, Object>> referencesById
    ) {
        return referenceIds == null
                ? List.of()
                : referenceIds.stream()
                .map(this::text)
                .filter(referencesById::containsKey)
                .distinct()
                .toList();
    }

    private String referenceDetails(
            List<String> referenceIds,
            Map<String, Map<String, Object>> referencesById
    ) {
        return referenceIds.stream()
                .map(referencesById::get)
                .filter(reference -> reference != null)
                .map(this::legalReferenceDetailLine)
                .filter(line -> !line.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private String legalReferenceDetailLine(Map<String, Object> reference) {
        return String.join("\t",
                safeCell(text(reference.get("referenceId"))),
                safeCell(text(reference.get("label"))),
                safeCell(text(reference.get("resolutionSource"))),
                safeCell(text(reference.get("bindingScope"))),
                safeCell(text(reference.get("bindingKey"))),
                safeCell(text(reference.get("relevance"))),
                safeCell(text(reference.get("catalogCode"))),
                safeCell(text(reference.get("catalogVersion"))),
                safeCell(text(reference.get("checklistItemCode"))));
    }

    private List<String> csvList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String summaryCode(SourceBackedLegalReviewStatus status) {
        return switch (status) {
            case PASS -> "LEGAL_REVIEW_PASSED";
            case WARN -> "LEGAL_REVIEW_NEEDS_HUMAN_REVIEW";
            case FAIL -> "LEGAL_REVIEW_BLOCKED";
            case INSUFFICIENT_CONTEXT -> "LEGAL_REVIEW_INSUFFICIENT_CONTEXT";
        };
    }

    private String summarySeverity(SourceBackedLegalReviewStatus status) {
        return switch (status) {
            case PASS -> "INFO";
            case WARN, INSUFFICIENT_CONTEXT -> "MEDIUM";
            case FAIL -> "HIGH";
        };
    }

    private String summaryMessage(SourceBackedLegalReviewResult result) {
        if (!result.summary().isBlank()) {
            return result.summary();
        }
        return switch (result.status()) {
            case PASS -> "법령 근거 기반 검토에서 확인 필요 항목이 발견되지 않았습니다.";
            case WARN -> "법령 근거 기반 검토에서 사람 확인이 필요한 항목이 있습니다.";
            case FAIL -> "법령 근거 기반 검토에서 문서 생성 전 처리해야 할 항목이 있습니다.";
            case INSUFFICIENT_CONTEXT -> "법령 근거 기반 검토에 필요한 근거가 충분하지 않습니다.";
        };
    }

    private String summaryEvidence(List<String> reviewedIds) {
        return reviewedIds.isEmpty()
                ? "reviewedReferenceCount=0"
                : "reviewedReferences=" + String.join(",", reviewedIds);
    }

    private void recordEvent(
            LegalReviewContext context,
            OperationEventSeverity severity,
            String eventType,
            String message,
            Map<String, Object> metadata
    ) {
        operationEventService.record(
                context.report().officeId(),
                severity,
                eventType,
                "report-preflight-review",
                workflowKey(context),
                "REPORT_PREFLIGHT_REVIEW_RUN",
                context.run().id(),
                context.run().requestedBy(),
                null,
                message,
                metadata == null ? Map.of() : metadata);
    }

    private String workflowKey(LegalReviewContext context) {
        return "report:" + context.report().id() + ":preflight-run:" + context.run().id();
    }

    private String part(String[] parts, int index) {
        return index >= 0 && index < parts.length ? parts[index].trim() : "";
    }

    private String safeCell(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return Map.copyOf(result);
        }
        return Map.of();
    }

    private List<Object> listValue(Object value) {
        if (value instanceof List<?> list) {
            return List.copyOf(list);
        }
        return List.of();
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        var text = text(value);
        if (text.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String upper(Object value) {
        return text(value).toUpperCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private enum LegalReferenceGrade {
        A,
        B,
        C,
        D,
        X
    }

    private record PassBlocker(
            String code,
            String category,
            String message
    ) {
        private Map<String, Object> toMap() {
            return Map.of(
                    "code", code,
                    "category", category,
                    "message", message);
        }
    }

    private record PassEligibility(
            boolean legalEligible,
            boolean evidenceEligible,
            boolean technicalCriteriaEligible,
            boolean applicabilityEligible,
            boolean finalEligible,
            List<PassBlocker> blockers
    ) {
        private Map<String, Object> toMap() {
            var values = new LinkedHashMap<String, Object>();
            values.put("legalEligible", legalEligible);
            values.put("evidenceEligible", evidenceEligible);
            values.put("technicalCriteriaEligible", technicalCriteriaEligible);
            values.put("applicabilityEligible", applicabilityEligible);
            values.put("finalEligible", finalEligible);
            values.put("blockers", blockers.stream().map(PassBlocker::toMap).toList());
            return Map.copyOf(values);
        }
    }

    private record LegalReferenceCoverage(
            int totalCount,
            int primaryCount,
            int supportingCount,
            int referenceCount,
            int candidateCount,
            int domainBindingCount,
            int catalogItemBindingCount,
            int reportTypeBindingCount,
            int corpusSearchCount,
            int businessItemAnchorCount,
            int reportTypeAnchorCount,
            boolean passEligibleForPass,
            String reviewStrength,
            LegalReferenceGrade legalReferenceGrade,
            PassEligibility passEligibility,
            List<String> limitations
    ) {
        private Map<String, Object> toMap() {
            var values = new LinkedHashMap<String, Object>();
            values.put("totalCount", totalCount);
            values.put("primaryCount", primaryCount);
            values.put("supportingCount", supportingCount);
            values.put("referenceCount", referenceCount);
            values.put("candidateCount", candidateCount);
            values.put("domainBindingCount", domainBindingCount);
            values.put("catalogItemBindingCount", catalogItemBindingCount);
            values.put("reportTypeBindingCount", reportTypeBindingCount);
            values.put("corpusSearchCount", corpusSearchCount);
            values.put("businessItemAnchorCount", businessItemAnchorCount);
            values.put("reportTypeAnchorCount", reportTypeAnchorCount);
            values.put("passEligibleForPass", passEligibleForPass);
            values.put("reviewStrength", reviewStrength);
            values.put("legalReferenceGrade", legalReferenceGrade.name());
            values.put("technicalCriteriaPassEligible", passEligibility.technicalCriteriaEligible());
            values.put("passEligibility", passEligibility.toMap());
            values.put("passBlockers", passEligibility.blockers().stream().map(PassBlocker::toMap).toList());
            values.put("limitations", limitations);
            return Map.copyOf(values);
        }
    }

    private record LegalReviewContext(
            ReportPreflightReviewRequest request,
            InspectionReport report,
            ReportPreflightReviewRun run,
            List<ReportPreflightReviewFinding> findings
    ) {
    }
}
