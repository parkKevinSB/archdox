package com.archdox.cloud.aiharness.application;

import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationCaseResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationGroupResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationSignalResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationSummaryResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiWorkerEvaluationReadService {
    private static final String PASS = "PASS";
    private static final String WARN = "WARN";

    private final PlatformAdminService platformAdminService;

    public AiWorkerEvaluationReadService(PlatformAdminService platformAdminService) {
        this.platformAdminService = platformAdminService;
    }

    @Transactional(readOnly = true)
    public AiWorkerEvaluationSummaryResponse summary(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        var groups = List.of(aiHarnessGroup(), workerControlGroup());
        var signals = signals();
        var totalCases = groups.stream().mapToInt(AiWorkerEvaluationGroupResponse::totalCases).sum();
        var automatedCases = groups.stream().mapToInt(AiWorkerEvaluationGroupResponse::automatedCases).sum();
        var passedCases = groups.stream().mapToInt(AiWorkerEvaluationGroupResponse::passedCases).sum();
        var warningCases = groups.stream().mapToInt(AiWorkerEvaluationGroupResponse::warningCases).sum();
        var failedCases = groups.stream().mapToInt(AiWorkerEvaluationGroupResponse::failedCases).sum();
        return new AiWorkerEvaluationSummaryResponse(
                OffsetDateTime.now(),
                "STATIC_BASELINE",
                "Numbers reflect deterministic ArchDox evaluation cases included in this build. Real-model evaluation runs are not executed by this endpoint.",
                totalCases,
                automatedCases,
                passedCases,
                warningCases,
                failedCases,
                percent(passedCases, totalCases),
                groups,
                signals);
    }

    private AiWorkerEvaluationGroupResponse aiHarnessGroup() {
        var cases = List.of(
                testCase("AI-H-001", "Legal digest keeps source-backed key articles", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.legalDigestEvaluationSetStaysSourceBacked"),
                testCase("AI-H-002", "Legal digest form/attachment change requires human review", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.legalDigestEvaluationSetStaysSourceBacked"),
                testCase("AI-H-003", "Conversation planner proposes only available actions", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.conversationPlannerEvaluationSetRespectsActionBoundary"),
                testCase("AI-H-004", "Conversation planner asks clarification when context is missing", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.conversationPlannerEvaluationSetRespectsActionBoundary"),
                testCase("AI-H-005", "Report preflight PASS has no findings", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.reportPreflightEvaluationSetKeepsFindingsStructured"),
                testCase("AI-H-006", "Report preflight preserves deterministic missing-photo finding", "AI_HARNESS",
                        "ArchDoxHarnessEvaluationSuiteTest.reportPreflightEvaluationSetKeepsFindingsStructured"));
        return group("AI_HARNESS_BASELINE", "AI Harness evaluation baseline", "AI_HARNESS", cases);
    }

    private AiWorkerEvaluationGroupResponse workerControlGroup() {
        var cases = List.of(
                testCase("WK-C-001", "Allowed action executes once", "WORKER_CONTROL",
                        "ArchDoxWorkerControlEvaluationSuiteTest.controlEvaluationSetMeasuresTerminalOutcomes"),
                testCase("WK-C-002", "Unknown action is rejected before policy", "WORKER_CONTROL",
                        "ArchDoxWorkerControlEvaluationSuiteTest.controlEvaluationSetMeasuresTerminalOutcomes"),
                testCase("WK-C-003", "Policy denial blocks executor", "WORKER_CONTROL",
                        "ArchDoxWorkerControlEvaluationSuiteTest.controlEvaluationSetMeasuresTerminalOutcomes"),
                testCase("WK-C-004", "Approval requirement blocks executor", "WORKER_CONTROL",
                        "ArchDoxWorkerControlEvaluationSuiteTest.controlEvaluationSetMeasuresTerminalOutcomes"),
                testCase("WK-C-005", "Run-control cancellation blocks after policy", "WORKER_CONTROL",
                        "ArchDoxWorkerControlEvaluationSuiteTest.controlEvaluationSetMeasuresTerminalOutcomes"),
                testCase("WK-C-006", "Executor failure is isolated as failed result", "WORKER_CONTROL",
                        "ArchDoxWorkerControlEvaluationSuiteTest.controlEvaluationSetMeasuresTerminalOutcomes"));
        return group("WORKER_CONTROL_BASELINE", "Worker control evaluation baseline", "WORKER_CONTROL", cases);
    }

    private List<AiWorkerEvaluationSignalResponse> signals() {
        return List.of(
                signal("MODEL_PROVIDER_SWITCHABLE", "Provider/model can be configured per harness", PASS,
                        "AI_HARNESS", "AiHarnessPolicyExecutionService + Admin AI Harness policy"),
                signal("OUTPUT_SCHEMA_VALIDATION", "AI output schema validation exists", PASS,
                        "AI_HARNESS", "JacksonPojoSchemaValidator in ArchDox harness factories"),
                signal("REFINE_RETRY", "Invalid model output can be refined/retried", PASS,
                        "AI_HARNESS", "MaxAttemptsRefinePolicy + harness tests"),
                signal("ACTION_REGISTRY", "Worker action registry exists", PASS,
                        "WORKER_CONTROL", "ArchDoxWorkerActionRegistry"),
                signal("POLICY_GATE", "Worker policy gate exists", PASS,
                        "WORKER_CONTROL", "GateArchDoxWorkerActionStep"),
                signal("RUN_CONTROL_CANCEL", "Execution can be cancelled before executor", PASS,
                        "WORKER_CONTROL", "CheckArchDoxWorkerRunControlStep"),
                signal("TRACE_AUDIT", "Harness/worker traces are observable", PASS,
                        "OBSERVABILITY", "AiHarnessTraceEvent + WorkerGovernanceReadService"),
                signal("REAL_MODEL_EVALUATION", "Real-model evaluation run history", WARN,
                        "EVALUATION", "Not yet a runtime evaluation runner. Current endpoint is a static baseline."));
    }

    private AiWorkerEvaluationCaseResponse testCase(String id, String name, String layer, String evidence) {
        return new AiWorkerEvaluationCaseResponse(
                id,
                name,
                layer,
                PASS,
                true,
                "GRADLE_TEST",
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
                cases);
    }

    private static int percent(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round((numerator * 100.0d) / denominator);
    }
}
