package com.archdox.cloud.reportai.application;

import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.application.AiModelCallMetadata;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.inspection.domain.InspectionReport;
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
import io.github.parkkevinsb.flower.ai.harness.flow.AiHarnessFlowFactory;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.refine.MaxAttemptsRefinePolicy;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStatus;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStore;
import io.github.parkkevinsb.flower.ai.harness.spi.TraceListener;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public void run(ReportPreflightReviewRequest request) {
        var context = context(request);
        var references = legalReferences(context.findings());
        if (references.isEmpty()) {
            saveSingleFinding(context, insufficientContextFinding(context, List.of()));
            recordEvent(context, OperationEventSeverity.WARN, "REPORT_PREFLIGHT_LEGAL_REVIEW_INSUFFICIENT_CONTEXT",
                    "Source-backed legal review skipped because no legal references were available.",
                    Map.of("legalReferenceCount", 0));
            return;
        }

        var policy = policyExecutionService.resolve(AiHarnessPolicyKey.SOURCE_BACKED_LEGAL_REVIEW);
        if (!policy.runnable()) {
            saveSingleFinding(context, skippedFinding(context, references, policy.unavailableReason()));
            recordEvent(context, OperationEventSeverity.WARN, "REPORT_PREFLIGHT_LEGAL_REVIEW_SKIPPED",
                    "Source-backed legal review AI policy is not runnable.",
                    Map.of("reason", policy.unavailableReason(), "legalReferenceCount", references.size()));
            return;
        }

        var plan = policy.plan();
        policyExecutionService.requireWithinBudget(plan);
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

        if (!aiWorker.submitAndAwait(flow, plan.timeout().plus(WAIT_GRACE))) {
            saveSingleFinding(context, failedFinding(context, references, "LEGAL_REVIEW_AI_TIMEOUT", "법령검토 AI 응답 시간이 초과되었습니다."));
            recordEvent(context, OperationEventSeverity.ERROR, "REPORT_PREFLIGHT_LEGAL_REVIEW_TIMEOUT",
                    "Source-backed legal review AI timed out.",
                    Map.of("harnessRunId", flow.context().runId().value()));
            return;
        }
        if (flow.context().status() != AiHarnessRunStatus.SUCCEEDED) {
            saveSingleFinding(context, failedFinding(
                    context,
                    references,
                    "LEGAL_REVIEW_AI_FAILED",
                    flow.context().terminalReason().orElse("법령검토 AI가 정상 완료되지 않았습니다.")));
            recordEvent(context, OperationEventSeverity.ERROR, "REPORT_PREFLIGHT_LEGAL_REVIEW_FAILED",
                    "Source-backed legal review AI failed.",
                    Map.of(
                            "harnessRunId", flow.context().runId().value(),
                            "status", flow.context().status().name()));
            return;
        }
        var result = result(flow.context().latestValidation());
        if (result.isEmpty()) {
            saveSingleFinding(context, failedFinding(context, references, "LEGAL_REVIEW_RESULT_INVALID", "법령검토 AI 응답을 해석하지 못했습니다."));
            return;
        }
        saveResult(context, references, result.get(), plan.provider().providerCode(), plan.modelId().asString(), flow.context().runId().value());
        recordEvent(context, OperationEventSeverity.INFO, "REPORT_PREFLIGHT_LEGAL_REVIEW_COMPLETED",
                "Source-backed legal review completed.",
                Map.of(
                        "harnessRunId", flow.context().runId().value(),
                        "status", result.get().status().name(),
                        "legalReferenceCount", references.size(),
                        "issueCount", result.get().issues().size()));
    }

    private LegalReviewContext context(ReportPreflightReviewRequest request) {
        var report = reportRepository.findByIdAndOfficeId(request.reportId(), request.officeId())
                .orElseThrow(() -> new NotFoundException("Inspection report not found"));
        var run = runRepository.findByIdAndOfficeIdAndReportId(request.reviewRunId(), request.officeId(), request.reportId())
                .orElseThrow(() -> new NotFoundException("Report preflight review run not found"));
        var findings = findingRepository.findByOfficeIdAndReviewRunIdOrderByIdAsc(request.officeId(), request.reviewRunId()).stream()
                .filter(finding -> !SOURCE.equals(finding.source()))
                .toList();
        return new LegalReviewContext(request, report, run, findings);
    }

    private SourceBackedLegalReviewInput input(
            LegalReviewContext context,
            List<Map<String, Object>> references
    ) {
        return new SourceBackedLegalReviewInput(
                String.valueOf(context.report().officeId()),
                String.valueOf(context.report().id()),
                context.report().reportType(),
                context.report().title(),
                context.report().contentRevision(),
                reportSnapshot(context.report()),
                stepSnapshot(context.report()),
                findingSummaries(context.findings()),
                references,
                legalReviewContext(context.findings(), references));
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
            List<Map<String, Object>> references
    ) {
        var context = new LinkedHashMap<String, Object>();
        context.put("purpose", "SOURCE_BACKED_LEGAL_REVIEW_DRAFT");
        context.put("mode", REVIEW_MODE);
        context.put("sourceBackedOnly", true);
        context.put("legalReferenceCount", references.size());
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
                "Do not modify report data or legal corpus."));
        return Map.copyOf(context);
    }

    private List<Map<String, Object>> legalReferences(List<ReportPreflightReviewFinding> findings) {
        var byId = new LinkedHashMap<String, Map<String, Object>>();
        for (var finding : findings) {
            parseLegalReferenceDetails(finding.attributesJson().get("legalReferenceDetails"))
                    .forEach(reference -> byId.putIfAbsent(text(reference.get("referenceId")), reference));
            csvList(finding.attributesJson().get("legalReferences")).forEach(referenceId -> byId.putIfAbsent(
                    referenceId,
                    Map.of("referenceId", referenceId)));
        }
        return byId.values().stream().toList();
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
        var findings = new java.util.ArrayList<ReportPreflightReviewFinding>();
        findings.add(summaryFinding(context, referencesById, result, reviewedIds, providerCode, modelId, harnessRunId));
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
        attributes.put("approvalRequired", "true");
        attributes.put("suggestion", issue.suggestion());
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

    private ReportPreflightReviewFinding insufficientContextFinding(
            LegalReviewContext context,
            List<Map<String, Object>> references
    ) {
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("source", SOURCE);
        attributes.put("category", "LEGAL_REVIEW");
        attributes.put("legalReviewStatus", SourceBackedLegalReviewStatus.INSUFFICIENT_CONTEXT.name());
        attributes.put("reviewMode", REVIEW_MODE);
        attributes.put("draftOnly", "true");
        attributes.put("approvalRequired", "true");
        attributes.put("limitations", "법령검토에 사용할 업무-법령 근거가 없거나 충분하지 않습니다.");
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
                "법령검토 AI 설정이 없어 전용 법령검토를 생략했습니다.",
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

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record LegalReviewContext(
            ReportPreflightReviewRequest request,
            InspectionReport report,
            ReportPreflightReviewRun run,
            List<ReportPreflightReviewFinding> findings
    ) {
    }
}
