package com.archdox.cloud.aiharness.application;

import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationCaseResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationGroupResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationSignalResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationSummaryResponse;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.application.AiModelCallMetadata;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.legal.application.FakeLegalSourceClient;
import com.archdox.cloud.legal.domain.LegalChangeDigest;
import com.archdox.cloud.legal.infra.LegalChangeDigestRepository;
import com.archdox.cloud.reportai.flow.ReportPreflightLegalReviewAiWorker;
import com.archdox.cloud.worker.ArchDoxWorkerServiceWorker;
import com.archdox.legalai.SourceBackedLegalReviewHarnessFactory;
import com.archdox.legalai.SourceBackedLegalReviewInput;
import com.archdox.legalai.SourceBackedLegalReviewResult;
import com.archdox.legalai.SourceBackedLegalReviewStatus;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionExecutionStatus;
import com.archdox.worker.domain.ArchDoxWorkerActionOrigin;
import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import com.archdox.worker.domain.ArchDoxWorkerRequestContext;
import com.archdox.worker.domain.ArchDoxWorkerRequestSource;
import com.archdox.worker.flow.ArchDoxWorkerExecutionFlowFactory;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiWorkerEvaluationRuntimeScenarioService {
    static final String EVALUATION_MODE = "RUNTIME_WORKER_SCENARIO";

    private static final String PASS = "PASS";
    private static final String WARN = "WARN";
    private static final String FAILED = "FAILED";

    private final AiWorkerEvaluationReadService readService;
    private final LegalChangeDigestRepository digestRepository;
    private final ArchDoxWorkerExecutionFlowFactory workerFlowFactory;
    private final ArchDoxWorkerServiceWorker workerServiceWorker;
    private final AiHarnessPolicyExecutionService aiHarnessPolicyExecutionService;
    private final AiWorkerEvaluationTokenControlService tokenControlService;
    private final ReportPreflightLegalReviewAiWorker legalReviewAiWorker;
    private final AiModelGateway aiModelGateway;
    private final ObjectMapper objectMapper;
    private final TraceListener aiHarnessTraceListener;

    public AiWorkerEvaluationRuntimeScenarioService(
            AiWorkerEvaluationReadService readService,
            LegalChangeDigestRepository digestRepository,
            ArchDoxWorkerExecutionFlowFactory workerFlowFactory,
            ArchDoxWorkerServiceWorker workerServiceWorker,
            AiHarnessPolicyExecutionService aiHarnessPolicyExecutionService,
            AiWorkerEvaluationTokenControlService tokenControlService,
            ReportPreflightLegalReviewAiWorker legalReviewAiWorker,
            AiModelGateway aiModelGateway,
            ObjectMapper objectMapper,
            TraceListener aiHarnessTraceListener
    ) {
        this.readService = readService;
        this.digestRepository = digestRepository;
        this.workerFlowFactory = workerFlowFactory;
        this.workerServiceWorker = workerServiceWorker;
        this.aiHarnessPolicyExecutionService = aiHarnessPolicyExecutionService;
        this.tokenControlService = tokenControlService;
        this.legalReviewAiWorker = legalReviewAiWorker;
        this.aiModelGateway = aiModelGateway;
        this.objectMapper = objectMapper;
        this.aiHarnessTraceListener = aiHarnessTraceListener;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AiWorkerEvaluationSummaryResponse runtimeScenario(UserPrincipal principal) {
        var baseline = readService.summary(principal);
        var scenarioGroup = legalDigestWorkerScenario(principal);
        var documentLegalReviewGroup = documentLegalReviewScenario(principal);
        var groups = new ArrayList<>(baseline.groups());
        groups.add(scenarioGroup);
        groups.add(documentLegalReviewGroup);
        var tokenGroups = tokenControlService.tokenControlGroups();
        groups.addAll(tokenGroups);
        var signals = new ArrayList<>(baseline.signals());
        signals.add(signal(
                "RUNTIME_WORKER_SCENARIO",
                "Runtime worker dry-run scenario",
                groupStatus(scenarioGroup),
                "EVALUATION",
                scenarioEvidence(scenarioGroup)));
        signals.add(signal(
                "RUNTIME_DOCUMENT_LEGAL_REVIEW",
                "Runtime document legal review scenario",
                groupStatus(documentLegalReviewGroup),
                "EVALUATION",
                documentLegalReviewEvidence(documentLegalReviewGroup)));
        signals.addAll(tokenControlService.tokenControlSignals(tokenGroups));
        return summary(groups, signals, dataPolicy(scenarioGroup, documentLegalReviewGroup));
    }

    private AiWorkerEvaluationGroupResponse legalDigestWorkerScenario(UserPrincipal principal) {
        var digests = digestRepository.findAllExcludingSourceCode(
                FakeLegalSourceClient.DEFAULT_SOURCE_CODE,
                PageRequest.of(0, 1));
        if (digests.isEmpty()) {
            return group(
                    "RUNTIME_WORKER_SCENARIO",
                    "Runtime worker dry-run scenario",
                    "EVALUATION",
                    List.of(testCase(
                            "RUN-SCENARIO-LEGAL-DIGEST-000",
                            "Legal digest scenario source data",
                            WARN,
                            "WORKER_DRY_RUN",
                            "No non-fake legal change digest is available for runtime scenario evaluation.")));
        }

        var digest = digests.get(0);
        var request = workerRequest(principal);
        var action = legalDigestAction(digest);
        var handle = workerFlowFactory.createHandle(request, action);
        var timeout = legalDigestTimeout();
        if (!workerServiceWorker.submitAndTrackAsync(handle.flow(), timeout).join()) {
            return group(
                    "RUNTIME_WORKER_SCENARIO",
                    "Runtime worker dry-run scenario",
                    "EVALUATION",
                    List.of(
                            testCase(
                                    "RUN-SCENARIO-LEGAL-DIGEST-001",
                                    "Legal digest worker dry-run execution",
                                    FAILED,
                                    "WORKER_DRY_RUN",
                                    "Worker dry-run timed out before terminal result. digestId="
                                            + digest.id() + ", changeSetId=" + digest.changeSetId()),
                            noPersistenceCase()));
        }

        var result = handle.result();
        if (result == null) {
            return group(
                    "RUNTIME_WORKER_SCENARIO",
                    "Runtime worker dry-run scenario",
                    "EVALUATION",
                    List.of(
                            testCase(
                                    "RUN-SCENARIO-LEGAL-DIGEST-001",
                                    "Legal digest worker dry-run execution",
                                    FAILED,
                                    "WORKER_DRY_RUN",
                                    "Worker flow finished without an action result. digestId="
                                            + digest.id() + ", changeSetId=" + digest.changeSetId()),
                            noPersistenceCase()));
        }

        var cases = new ArrayList<AiWorkerEvaluationCaseResponse>();
        cases.add(testCase(
                "RUN-SCENARIO-LEGAL-DIGEST-001",
                "Legal digest worker dry-run execution",
                workerResultStatus(result),
                "WORKER_DRY_RUN",
                workerResultEvidence(digest, request.requestId(), result)));
        cases.add(testCase(
                "RUN-SCENARIO-LEGAL-DIGEST-002",
                "Legal digest worker output safety",
                outputSafetyStatus(result),
                "WORKER_OUTPUT_SAFETY",
                outputSafetyEvidence(result)));
        cases.add(testCase(
                "RUN-SCENARIO-LEGAL-DIGEST-003",
                "Legal digest harness traceability",
                harnessTraceabilityStatus(result),
                "WORKER_TRACE_SCENARIO",
                harnessTraceabilityEvidence(result)));
        cases.add(noPersistenceCase());
        return group(
                "RUNTIME_WORKER_SCENARIO",
                "Runtime worker dry-run scenario",
                "EVALUATION",
                cases);
    }

    private AiWorkerEvaluationGroupResponse documentLegalReviewScenario(UserPrincipal principal) {
        var resolution = aiHarnessPolicyExecutionService.resolve(AiHarnessPolicyKey.SOURCE_BACKED_LEGAL_REVIEW);
        if (!resolution.runnable()) {
            return group(
                    "RUNTIME_DOCUMENT_LEGAL_REVIEW_SCENARIO",
                    "Runtime document legal review scenario",
                    "EVALUATION",
                    List.of(testCase(
                            "RUN-SCENARIO-DOC-LEGAL-000",
                            "Document legal review harness policy",
                            WARN,
                            "REAL_MODEL_LEGAL_REVIEW",
                            "SOURCE_BACKED_LEGAL_REVIEW policy is not runnable: " + resolution.unavailableReason())));
        }

        var plan = resolution.plan();
        if (fakeProvider(plan.provider().providerCode())) {
            return group(
                    "RUNTIME_DOCUMENT_LEGAL_REVIEW_SCENARIO",
                    "Runtime document legal review scenario",
                    "EVALUATION",
                    List.of(testCase(
                            "RUN-SCENARIO-DOC-LEGAL-000",
                            "Document legal review real provider requirement",
                            WARN,
                            "REAL_MODEL_LEGAL_REVIEW",
                            "SOURCE_BACKED_LEGAL_REVIEW resolves to development/fake provider "
                                    + plan.provider().providerCode()
                                    + ". No external model legal review scenario was executed.")));
        }

        try {
            aiHarnessPolicyExecutionService.requireWithinBudget(plan);
        } catch (RuntimeException ex) {
            return group(
                    "RUNTIME_DOCUMENT_LEGAL_REVIEW_SCENARIO",
                    "Runtime document legal review scenario",
                    "EVALUATION",
                    List.of(testCase(
                            "RUN-SCENARIO-DOC-LEGAL-000",
                            "Document legal review budget guard",
                            WARN,
                            "REAL_MODEL_LEGAL_REVIEW",
                            "Budget guard blocked the real-model legal review scenario: " + ex.getMessage())));
        }

        var input = documentLegalReviewInput();
        var allowedReferenceIds = input.sourceBackedLegalReferences().stream()
                .map(reference -> text(reference.get("referenceId")))
                .filter(value -> !value.isBlank())
                .toList();
        var primaryReferenceId = allowedReferenceIds.isEmpty() ? "" : allowedReferenceIds.get(0);
        var workflowKey = "evaluation:document-legal-review:" + UUID.randomUUID();
        var spec = new SourceBackedLegalReviewHarnessFactory(objectMapper).spec(
                (findings, ctx) -> {
                },
                AiHarnessRunStore.noop(),
                new MaxAttemptsRefinePolicy(plan.maxAttempts()),
                aiHarnessTraceListener);
        var flow = new AiHarnessFlowFactory<>(aiModelGateway, spec, Instant::now)
                .createFlow(input, AiHarnessFlowFactory.RunOverrides.builder()
                        .modelId(plan.modelId())
                        .timeout(plan.timeout())
                        .providerOptions(AiModelCallMetadata.options(
                                0L,
                                principal.userId(),
                                AiHarnessPolicyKey.SOURCE_BACKED_LEGAL_REVIEW.name(),
                                "evaluation-document-legal-review",
                                workflowKey,
                                "AI_WORKER_EVALUATION_RUN",
                                workflowKey,
                                Map.of(
                                        "archdox.evaluationScenario", "DOCUMENT_LEGAL_REVIEW",
                                        "archdox.dryRun", true),
                                plan.maxOutputTokens()))
                        .build());

        var startedAt = System.nanoTime();
        var awaited = legalReviewAiWorker.submitAndTrackAsync(flow, plan.timeout().plusSeconds(5)).join();
        var elapsedMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        if (!awaited) {
            return group(
                    "RUNTIME_DOCUMENT_LEGAL_REVIEW_SCENARIO",
                    "Runtime document legal review scenario",
                    "EVALUATION",
                    List.of(
                            testCase(
                                    "RUN-SCENARIO-DOC-LEGAL-001",
                                    "Document legal review real-model execution",
                                    FAILED,
                                    "REAL_MODEL_LEGAL_REVIEW",
                                    "Source-backed legal review scenario timed out. provider="
                                            + plan.provider().providerCode()
                                            + ", model=" + plan.modelId().asString()
                                            + ", harnessRunId=" + flow.context().runId().value()
                                            + ", elapsedMs=" + elapsedMs),
                            documentLegalReviewNoPersistenceCase()));
        }
        if (flow.context().status() != AiHarnessRunStatus.SUCCEEDED) {
            return group(
                    "RUNTIME_DOCUMENT_LEGAL_REVIEW_SCENARIO",
                    "Runtime document legal review scenario",
                    "EVALUATION",
                    List.of(
                            testCase(
                                    "RUN-SCENARIO-DOC-LEGAL-001",
                                    "Document legal review real-model execution",
                                    FAILED,
                                    "REAL_MODEL_LEGAL_REVIEW",
                                    "Source-backed legal review scenario failed. status="
                                            + flow.context().status().name()
                                            + ", reason=" + flow.context().terminalReason().orElse("-")
                                            + ", provider=" + plan.provider().providerCode()
                                            + ", model=" + plan.modelId().asString()
                                            + ", harnessRunId=" + flow.context().runId().value()
                                            + ", elapsedMs=" + elapsedMs),
                            documentLegalReviewNoPersistenceCase()));
        }

        var result = legalReviewResult(flow.context().latestValidation());
        if (result.isEmpty()) {
            return group(
                    "RUNTIME_DOCUMENT_LEGAL_REVIEW_SCENARIO",
                    "Runtime document legal review scenario",
                    "EVALUATION",
                    List.of(
                            testCase(
                                    "RUN-SCENARIO-DOC-LEGAL-001",
                                    "Document legal review real-model execution",
                                    FAILED,
                                    "REAL_MODEL_LEGAL_REVIEW",
                                    "Source-backed legal review scenario completed but did not produce a valid result. provider="
                                            + plan.provider().providerCode()
                                            + ", model=" + plan.modelId().asString()
                                            + ", harnessRunId=" + flow.context().runId().value()
                                            + ", elapsedMs=" + elapsedMs),
                            documentLegalReviewNoPersistenceCase()));
        }

        var legalResult = result.get();
        var cases = new ArrayList<AiWorkerEvaluationCaseResponse>();
        cases.add(testCase(
                "RUN-SCENARIO-DOC-LEGAL-001",
                "Document legal review real-model execution",
                PASS,
                "REAL_MODEL_LEGAL_REVIEW",
                "Harness run succeeded. provider=" + plan.provider().providerCode()
                        + ", model=" + plan.modelId().asString()
                        + ", harnessRunId=" + flow.context().runId().value()
                        + ", elapsedMs=" + elapsedMs
                        + ", resultStatus=" + legalResult.status().name()
                        + ", confidence=" + legalResult.confidence().name()));
        cases.add(testCase(
                "RUN-SCENARIO-DOC-LEGAL-002",
                "Document legal review source boundary",
                sourceBoundaryStatus(legalResult, allowedReferenceIds, primaryReferenceId),
                "REAL_MODEL_LEGAL_REVIEW",
                sourceBoundaryEvidence(legalResult, allowedReferenceIds, primaryReferenceId)));
        cases.add(testCase(
                "RUN-SCENARIO-DOC-LEGAL-003",
                "Document legal review disposition quality",
                legalDispositionStatus(legalResult),
                "REAL_MODEL_LEGAL_REVIEW",
                legalDispositionEvidence(legalResult)));
        cases.add(testCase(
                "RUN-SCENARIO-DOC-LEGAL-004",
                "Document legal review cautious wording",
                cautiousWordingStatus(legalResult),
                "REAL_MODEL_LEGAL_REVIEW",
                cautiousWordingEvidence(legalResult)));
        cases.add(documentLegalReviewNoPersistenceCase());
        return group(
                "RUNTIME_DOCUMENT_LEGAL_REVIEW_SCENARIO",
                "Runtime document legal review scenario",
                "EVALUATION",
                cases);
    }

    private ArchDoxWorkerRequest workerRequest(UserPrincipal principal) {
        return new ArchDoxWorkerRequest(
                UUID.randomUUID(),
                ArchDoxWorkerRequestSource.SYSTEM,
                "Evaluate legal digest worker dry-run scenario",
                new ArchDoxWorkerRequestContext(principal.userId(), null, null, null, null, null, "ko-KR"),
                Instant.now());
    }

    private ArchDoxWorkerAction legalDigestAction(LegalChangeDigest digest) {
        return new ArchDoxWorkerAction(
                ArchDoxWorkerActionType.ENRICH_LEGAL_CHANGE_DIGEST,
                Map.of(
                        "digestId", digest.id(),
                        "changeSetId", digest.changeSetId(),
                        "dryRun", true,
                        "evaluationScenario", true),
                "Evaluate source-backed legal digest AI draft dry-run without persisting the draft.",
                1.0d,
                ArchDoxWorkerActionOrigin.SYSTEM);
    }

    private Duration legalDigestTimeout() {
        var resolution = aiHarnessPolicyExecutionService.resolve(AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT);
        if (resolution.runnable()) {
            return resolution.plan().timeout().plusSeconds(5);
        }
        return Duration.ofSeconds(15);
    }

    private String workerResultStatus(ArchDoxWorkerActionResult result) {
        if (result.status() == ArchDoxWorkerActionExecutionStatus.SUCCEEDED) {
            return PASS;
        }
        if ("LEGAL_DIGEST_AI_NOT_CONFIGURED".equals(result.resultCode())) {
            return WARN;
        }
        return FAILED;
    }

    private String outputSafetyStatus(ArchDoxWorkerActionResult result) {
        var output = result.output();
        if (booleanValue(output.get("publicationApplied"))
                || booleanValue(output.get("corpusMutated"))
                || booleanValue(output.get("digestMutated"))) {
            return FAILED;
        }
        return PASS;
    }

    private String harnessTraceabilityStatus(ArchDoxWorkerActionResult result) {
        if (result.status() != ArchDoxWorkerActionExecutionStatus.SUCCEEDED) {
            return WARN;
        }
        var output = result.output();
        if (text(output.get("aiHarnessRunId")).isBlank()) {
            return WARN;
        }
        if (stringList(output.get("keyArticles")).isEmpty()) {
            return WARN;
        }
        return PASS;
    }

    private String workerResultEvidence(
            LegalChangeDigest digest,
            UUID workerRequestId,
            ArchDoxWorkerActionResult result
    ) {
        return "Worker result: status=" + result.status()
                + ", code=" + result.resultCode()
                + ", workerRequestId=" + workerRequestId
                + ", digestId=" + digest.id()
                + ", changeSetId=" + digest.changeSetId()
                + ", message=" + result.message();
    }

    private String outputSafetyEvidence(ArchDoxWorkerActionResult result) {
        var output = result.output();
        return "Output flags: publicationApplied=" + booleanValue(output.get("publicationApplied"))
                + ", corpusMutated=" + booleanValue(output.get("corpusMutated"))
                + ", digestMutated=" + booleanValue(output.get("digestMutated")) + ".";
    }

    private String harnessTraceabilityEvidence(ArchDoxWorkerActionResult result) {
        var output = result.output();
        return "Harness run=" + valueOrDash(text(output.get("aiHarnessRunId")))
                + ", keyArticles=" + stringList(output.get("keyArticles")).size()
                + ", draftStatus=" + valueOrDash(text(output.get("digestDraftStatus"))) + ".";
    }

    private AiWorkerEvaluationCaseResponse noPersistenceCase() {
        return testCase(
                "RUN-SCENARIO-LEGAL-DIGEST-004",
                "Legal digest scenario persistence boundary",
                PASS,
                "WORKER_OUTPUT_SAFETY",
                "Scenario runner inspects the worker dry-run result only; it does not save LegalDigestAiDraft or apply publication changes.");
    }

    private AiWorkerEvaluationCaseResponse documentLegalReviewNoPersistenceCase() {
        return testCase(
                "RUN-SCENARIO-DOC-LEGAL-005",
                "Document legal review scenario persistence boundary",
                PASS,
                "REAL_MODEL_LEGAL_REVIEW",
                "Scenario uses an in-memory evaluation report input and does not create or mutate InspectionReport, review run, finding, document job, or legal corpus records.");
    }

    private String sourceBoundaryStatus(
            SourceBackedLegalReviewResult result,
            List<String> allowedReferenceIds,
            String primaryReferenceId
    ) {
        var reviewed = result.reviewedReferenceIds();
        if (reviewed.stream().anyMatch(referenceId -> !allowedReferenceIds.contains(referenceId))) {
            return FAILED;
        }
        if (reviewed.isEmpty()) {
            return WARN;
        }
        if (!primaryReferenceId.isBlank() && !reviewed.contains(primaryReferenceId)) {
            return WARN;
        }
        return PASS;
    }

    private String sourceBoundaryEvidence(
            SourceBackedLegalReviewResult result,
            List<String> allowedReferenceIds,
            String primaryReferenceId
    ) {
        var outside = result.reviewedReferenceIds().stream()
                .filter(referenceId -> !allowedReferenceIds.contains(referenceId))
                .toList();
        if (!outside.isEmpty()) {
            return "Model cited reference IDs outside the supplied source-backed anchors: " + outside
                    + ". Allowed=" + allowedReferenceIds;
        }
        if (result.reviewedReferenceIds().isEmpty()) {
            return "Model returned no reviewedReferenceIds. Allowed=" + allowedReferenceIds;
        }
        if (!primaryReferenceId.isBlank() && !result.reviewedReferenceIds().contains(primaryReferenceId)) {
            return "Model stayed inside supplied IDs but did not review the primary business-item anchor "
                    + primaryReferenceId + ". reviewed=" + result.reviewedReferenceIds();
        }
        return "Reviewed references stayed inside supplied anchors and included the primary business-item anchor. reviewed="
                + result.reviewedReferenceIds();
    }

    private String legalDispositionStatus(SourceBackedLegalReviewResult result) {
        if (result.status() == SourceBackedLegalReviewStatus.PASS) {
            return PASS;
        }
        if (result.status() == SourceBackedLegalReviewStatus.FAIL) {
            return FAILED;
        }
        return WARN;
    }

    private String legalDispositionEvidence(SourceBackedLegalReviewResult result) {
        return "Legal review result status=" + result.status().name()
                + ", issueCount=" + result.issues().size()
                + ", summary=" + truncate(result.summary(), 180)
                + ", limitations=" + truncate(result.limitations(), 180);
    }

    private String cautiousWordingStatus(SourceBackedLegalReviewResult result) {
        if (containsFinalComplianceWording(result.summary()
                + "\n" + result.legalReviewScope()
                + "\n" + result.passReason()
                + "\n" + result.limitations())) {
            return FAILED;
        }
        if (result.status() == SourceBackedLegalReviewStatus.PASS && result.limitations().isBlank()) {
            return WARN;
        }
        return PASS;
    }

    private String cautiousWordingEvidence(SourceBackedLegalReviewResult result) {
        if (containsFinalComplianceWording(result.summary()
                + "\n" + result.legalReviewScope()
                + "\n" + result.passReason()
                + "\n" + result.limitations())) {
            return "Model used final legal-compliance wording in summary/scope/passReason/limitations.";
        }
        if (result.status() == SourceBackedLegalReviewStatus.PASS && result.limitations().isBlank()) {
            return "PASS result used cautious wording but did not state limitations.";
        }
        return "No final legal-compliance wording detected. limitations=" + truncate(result.limitations(), 180);
    }

    private Optional<SourceBackedLegalReviewResult> legalReviewResult(Optional<ValidationResult<?>> validation) {
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

    private SourceBackedLegalReviewInput documentLegalReviewInput() {
        var primary = new LinkedHashMap<String, Object>();
        primary.put("referenceId", "BUILDING_ACT:0025001@evaluation");
        primary.put("label", "건축법 25 건축물의 공사감리");
        primary.put("resolutionSource", "LEGAL_DOMAIN_BINDING");
        primary.put("bindingScope", "CATALOG_ITEM");
        primary.put("bindingKey", "STEEL_MEMBER_SYMBOL");
        primary.put("relevance", "PRIMARY");
        primary.put("catalogCode", "CONSTRUCTION_SUPERVISION_CHECKLIST");
        primary.put("catalogVersion", "evaluation-v1");
        primary.put("checklistItemCode", "STEEL_MEMBER_SYMBOL");
        primary.put("anchorRole", "BUSINESS_ITEM_ANCHOR");
        primary.put("referencePriorityScore", 795);

        var supporting = new LinkedHashMap<String, Object>();
        supporting.put("referenceId", "CONSTRUCTION_SUPERVISION_DETAILED_STANDARD:000100@evaluation");
        supporting.put("label", "건축공사 감리세부기준 별표 0001 단계별 감리 체크리스트 대장");
        supporting.put("resolutionSource", "LEGAL_DOMAIN_BINDING");
        supporting.put("bindingScope", "CATALOG_ITEM");
        supporting.put("bindingKey", "STEEL_MEMBER_SYMBOL");
        supporting.put("relevance", "SUPPORTING");
        supporting.put("catalogCode", "CONSTRUCTION_SUPERVISION_CHECKLIST");
        supporting.put("catalogVersion", "evaluation-v1");
        supporting.put("checklistItemCode", "STEEL_MEMBER_SYMBOL");
        supporting.put("anchorRole", "BUSINESS_ITEM_ANCHOR");
        supporting.put("referencePriorityScore", 720);

        var candidate = new LinkedHashMap<String, Object>();
        candidate.put("referenceId", "CONSTRUCTION_SUPERVISION_DETAILED_STANDARD:BODY@evaluation");
        candidate.put("label", "건축공사 감리세부기준 본문");
        candidate.put("resolutionSource", "LEGAL_SEARCH");
        candidate.put("bindingScope", "LEGAL_CORPUS_SEARCH");
        candidate.put("bindingKey", "");
        candidate.put("relevance", "CANDIDATE");
        candidate.put("catalogCode", "");
        candidate.put("catalogVersion", "");
        candidate.put("checklistItemCode", "");
        candidate.put("anchorRole", "SEARCH_CANDIDATE");
        candidate.put("referencePriorityScore", 160);

        return new SourceBackedLegalReviewInput(
                "0",
                "evaluation-report",
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "평가용 공사감리일지",
                1,
                Map.of(
                        "reportType", "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                        "status", "SUBMITTED",
                        "photoEvidenceStatus", Map.of(
                                "allDailyLogPhotoRefsResolved", true,
                                "generationBlockingPhotoIssue", false,
                                "photoSourceOfTruth", "EVALUATION_IN_MEMORY")),
                Map.of(
                        "DAILY_LOG", Map.of(
                                "payloadStorageMode", "EVALUATION_IN_MEMORY",
                                "clientRevision", 1,
                                "payload", Map.of(
                                        "dailyItems", Map.of("groups", List.of(Map.of(
                                                "tradeCode", "STEEL",
                                                "tradeName", "철골공사",
                                                "entries", List.of(Map.of(
                                                        "inspectionItemCode", "STEEL_MEMBER_SYMBOL",
                                                        "inspectionItemName", "기둥·보 부호",
                                                        "supervisionContent", "철골 부재의 기둥·보 부호와 접합 상태를 확인하고 사진 증거와 대조했습니다.",
                                                        "photoIds", List.of(1001)))))),
                                        "issueAndAction", "특이사항 없이 감리 확인을 완료했습니다.",
                                        "nextAction", "다음 공정 진행 시 접합부 상태와 사진 증거를 계속 확인할 예정입니다."))),
                List.of(),
                List.of(Map.copyOf(primary), Map.copyOf(supporting), Map.copyOf(candidate)),
                Map.of(
                        "purpose", "RUNTIME_DOCUMENT_LEGAL_REVIEW_EVALUATION",
                        "referenceCoverage", Map.of(
                                "totalCount", 3,
                                "primaryCount", 1,
                                "supportingCount", 1,
                                "candidateCount", 1,
                                "domainBindingCount", 2,
                                "businessItemAnchorCount", 2,
                                "passEligibleForPass", true,
                                "reviewStrength", "HIGH",
                                "limitations", List.of("일부 근거는 법령 검색 후보이므로 사람 확인이 필요합니다.")),
                        "reportEvidenceChecklist", Map.of(
                                "dailyLogGroupCount", 1,
                                "dailyLogEntryCount", 1,
                                "dailyLogEntriesWithSupervisionContent", 1,
                                "dailyLogEntriesWithPhotoIds", 1,
                                "dailyLogEntriesWithChecklistItemCode", 1,
                                "hasIssueAndAction", true,
                                "hasNextAction", true,
                                "allDailyLogPhotoRefsResolved", true,
                                "generationBlockingPhotoIssue", false,
                                "photoEvidenceSource", "EVALUATION_IN_MEMORY"),
                        "rules", List.of(
                                "Use only sourceBackedLegalReferences.",
                                "PASS is allowed only when referenceCoverage.passEligibleForPass is true.",
                                "Do not claim final legal compliance.")));
    }

    private String scenarioEvidence(AiWorkerEvaluationGroupResponse group) {
        if (group.failedCases() > 0) {
            return group.failedCases() + " runtime worker scenario case(s) failed.";
        }
        if (group.warningCases() > 0) {
            return group.warningCases() + " runtime worker scenario case(s) need review.";
        }
        return "Runtime worker scenario completed without warnings.";
    }

    private String documentLegalReviewEvidence(AiWorkerEvaluationGroupResponse group) {
        if (group.failedCases() > 0) {
            return group.failedCases() + " document legal review scenario case(s) failed.";
        }
        if (group.warningCases() > 0) {
            return group.warningCases() + " document legal review scenario case(s) need review.";
        }
        return "Document legal review real-model scenario completed without warnings.";
    }

    private String dataPolicy(
            AiWorkerEvaluationGroupResponse legalDigestGroup,
            AiWorkerEvaluationGroupResponse documentLegalReviewGroup
    ) {
        return "Runtime scenario submits one Legal Digest Worker dry-run through ArchDoxWorkerExecutionFlowFactory "
                + "and one in-memory document legal review scenario through SOURCE_BACKED_LEGAL_REVIEW when a real provider is configured. "
                + "It does not persist an AI draft and does not mutate legal corpus or published digest state. "
                + "The document legal review scenario does not create or mutate report, finding, document job, or legal corpus records. "
                + "If the selected harness policies use real providers, external model calls may occur. "
                + scenarioEvidence(legalDigestGroup) + " " + documentLegalReviewEvidence(documentLegalReviewGroup);
    }

    private AiWorkerEvaluationSummaryResponse summary(
            List<AiWorkerEvaluationGroupResponse> groups,
            List<AiWorkerEvaluationSignalResponse> signals,
            String dataPolicy
    ) {
        var totalCases = groups.stream().mapToInt(AiWorkerEvaluationGroupResponse::totalCases).sum();
        var automatedCases = groups.stream().mapToInt(AiWorkerEvaluationGroupResponse::automatedCases).sum();
        var passedCases = groups.stream().mapToInt(AiWorkerEvaluationGroupResponse::passedCases).sum();
        var warningCases = groups.stream().mapToInt(AiWorkerEvaluationGroupResponse::warningCases).sum();
        var failedCases = groups.stream().mapToInt(AiWorkerEvaluationGroupResponse::failedCases).sum();
        return new AiWorkerEvaluationSummaryResponse(
                OffsetDateTime.now(),
                EVALUATION_MODE,
                dataPolicy,
                totalCases,
                automatedCases,
                passedCases,
                warningCases,
                failedCases,
                percent(passedCases, totalCases),
                List.copyOf(groups),
                List.copyOf(signals));
    }

    private AiWorkerEvaluationGroupResponse group(
            String groupKey,
            String displayName,
            String layer,
            List<AiWorkerEvaluationCaseResponse> cases
    ) {
        var passed = (int) cases.stream().filter(item -> PASS.equals(item.status())).count();
        var warnings = (int) cases.stream().filter(item -> WARN.equals(item.status())).count();
        var failed = cases.size() - passed - warnings;
        var automated = (int) cases.stream().filter(AiWorkerEvaluationCaseResponse::automated).count();
        return new AiWorkerEvaluationGroupResponse(
                groupKey,
                displayName,
                layer,
                cases.size(),
                automated,
                passed,
                warnings,
                failed,
                percent(passed, cases.size()),
                List.copyOf(cases));
    }

    private AiWorkerEvaluationCaseResponse testCase(
            String id,
            String name,
            String status,
            String verification,
            String evidence
    ) {
        return new AiWorkerEvaluationCaseResponse(
                id,
                name,
                "EVALUATION",
                status,
                true,
                verification,
                evidence);
    }

    private AiWorkerEvaluationSignalResponse signal(
            String key,
            String displayName,
            String status,
            String layer,
            String evidence
    ) {
        return new AiWorkerEvaluationSignalResponse(key, displayName, status, layer, evidence);
    }

    private String groupStatus(AiWorkerEvaluationGroupResponse group) {
        if (group.failedCases() > 0) {
            return FAILED;
        }
        if (group.warningCases() > 0) {
            return WARN;
        }
        return PASS;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim());
        }
        return false;
    }

    private List<String> stringList(Object value) {
        if (value instanceof Iterable<?> iterable) {
            var values = new ArrayList<String>();
            for (var item : iterable) {
                var text = text(item);
                if (!text.isBlank()) {
                    values.add(text);
                }
            }
            return values;
        }
        return List.of();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private boolean fakeProvider(String providerCode) {
        var code = providerCode == null ? "" : providerCode.trim().toLowerCase(Locale.ROOT);
        return code.startsWith("fake-") || code.contains("-fake") || code.contains("fake");
    }

    private boolean containsFinalComplianceWording(String value) {
        var normalized = text(value).replace(" ", "");
        if (normalized.isBlank()) {
            return false;
        }
        return normalized.contains("법적요구사항을충족")
                || normalized.contains("법적요건을충족")
                || normalized.contains("법령요건을충족")
                || normalized.contains("법령에부합")
                || normalized.contains("법에부합")
                || normalized.contains("법령을준수")
                || normalized.contains("법을준수")
                || normalized.contains("위반사항없")
                || normalized.contains("법적위험이없")
                || normalized.contains("법률리스크가없");
    }

    private String truncate(String value, int limit) {
        var text = text(value);
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, Math.max(0, limit - 3)) + "...";
    }

    private static int percent(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round((numerator * 100.0d) / denominator);
    }
}
