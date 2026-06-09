package com.archdox.cloud.aiharness.application;

import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationCaseResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationGroupResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationSignalResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationSummaryResponse;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.legal.application.FakeLegalSourceClient;
import com.archdox.cloud.legal.domain.LegalChangeDigest;
import com.archdox.cloud.legal.infra.LegalChangeDigestRepository;
import com.archdox.cloud.worker.ArchDoxWorkerServiceWorker;
import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerActionExecutionStatus;
import com.archdox.worker.domain.ArchDoxWorkerActionOrigin;
import com.archdox.worker.domain.ArchDoxWorkerActionResult;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import com.archdox.worker.domain.ArchDoxWorkerRequestContext;
import com.archdox.worker.domain.ArchDoxWorkerRequestSource;
import com.archdox.worker.flow.ArchDoxWorkerExecutionFlowFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    public AiWorkerEvaluationRuntimeScenarioService(
            AiWorkerEvaluationReadService readService,
            LegalChangeDigestRepository digestRepository,
            ArchDoxWorkerExecutionFlowFactory workerFlowFactory,
            ArchDoxWorkerServiceWorker workerServiceWorker,
            AiHarnessPolicyExecutionService aiHarnessPolicyExecutionService,
            AiWorkerEvaluationTokenControlService tokenControlService
    ) {
        this.readService = readService;
        this.digestRepository = digestRepository;
        this.workerFlowFactory = workerFlowFactory;
        this.workerServiceWorker = workerServiceWorker;
        this.aiHarnessPolicyExecutionService = aiHarnessPolicyExecutionService;
        this.tokenControlService = tokenControlService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AiWorkerEvaluationSummaryResponse runtimeScenario(UserPrincipal principal) {
        var baseline = readService.summary(principal);
        var scenarioGroup = legalDigestWorkerScenario(principal);
        var groups = new ArrayList<>(baseline.groups());
        groups.add(scenarioGroup);
        var tokenGroups = tokenControlService.tokenControlGroups();
        groups.addAll(tokenGroups);
        var signals = new ArrayList<>(baseline.signals());
        signals.add(signal(
                "RUNTIME_WORKER_SCENARIO",
                "Runtime worker dry-run scenario",
                groupStatus(scenarioGroup),
                "EVALUATION",
                scenarioEvidence(scenarioGroup)));
        signals.addAll(tokenControlService.tokenControlSignals(tokenGroups));
        return summary(groups, signals, dataPolicy(scenarioGroup));
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
        if (!workerServiceWorker.submitAndAwait(handle.flow(), timeout)) {
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

    private String scenarioEvidence(AiWorkerEvaluationGroupResponse group) {
        if (group.failedCases() > 0) {
            return group.failedCases() + " runtime worker scenario case(s) failed.";
        }
        if (group.warningCases() > 0) {
            return group.warningCases() + " runtime worker scenario case(s) need review.";
        }
        return "Runtime worker scenario completed without warnings.";
    }

    private String dataPolicy(AiWorkerEvaluationGroupResponse group) {
        return "Runtime scenario submits one Legal Digest Worker dry-run through ArchDoxWorkerExecutionFlowFactory. "
                + "It does not persist an AI draft and does not mutate legal corpus or published digest state. "
                + "If the selected legal digest harness uses a real provider, the harness may perform one external model call. "
                + scenarioEvidence(group);
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

    private static int percent(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round((numerator * 100.0d) / denominator);
    }
}
