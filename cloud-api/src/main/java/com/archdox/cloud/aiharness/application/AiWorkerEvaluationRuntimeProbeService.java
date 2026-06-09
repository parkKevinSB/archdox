package com.archdox.cloud.aiharness.application;

import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationCaseResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationGroupResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationSignalResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationSummaryResponse;
import com.archdox.cloud.aipolicy.application.AiHarnessExecutionPlan;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.application.AiProviderConnectionTestService;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.aipolicy.domain.AiProviderType;
import com.archdox.cloud.aipolicy.dto.AiProviderConnectionTestResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiWorkerEvaluationRuntimeProbeService {
    static final String EVALUATION_MODE = "RUNTIME_PROVIDER_PROBE";

    private static final String PASS = "PASS";
    private static final String WARN = "WARN";
    private static final String FAILED = "FAILED";

    private final AiWorkerEvaluationReadService readService;
    private final AiHarnessPolicyExecutionService policyExecutionService;
    private final AiProviderConnectionTestService connectionTestService;
    private final AiWorkerEvaluationTokenControlService tokenControlService;

    public AiWorkerEvaluationRuntimeProbeService(
            AiWorkerEvaluationReadService readService,
            AiHarnessPolicyExecutionService policyExecutionService,
            AiProviderConnectionTestService connectionTestService,
            AiWorkerEvaluationTokenControlService tokenControlService
    ) {
        this.readService = readService;
        this.policyExecutionService = policyExecutionService;
        this.connectionTestService = connectionTestService;
        this.tokenControlService = tokenControlService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AiWorkerEvaluationSummaryResponse runtimeProbe(UserPrincipal principal) {
        var baseline = readService.summary(principal);
        var runtimeCases = new ArrayList<AiWorkerEvaluationCaseResponse>();
        var providerPlans = new LinkedHashMap<Long, AiHarnessExecutionPlan>();
        var unavailableHarnessCount = 0;
        var fakeProviderCount = 0;
        var realProviderTestedCount = 0;
        var failedRealProviderCount = 0;

        for (var key : AiHarnessPolicyKey.values()) {
            var resolution = policyExecutionService.resolve(key);
            if (!resolution.runnable()) {
                unavailableHarnessCount++;
                runtimeCases.add(testCase(
                        "RUN-AI-POLICY-" + key.name(),
                        key.displayName() + " 실행 정책 확인",
                        WARN,
                        "RUNTIME_PROBE",
                        "Harness policy is not runnable: " + resolution.unavailableReason()));
                continue;
            }
            var plan = resolution.plan();
            runtimeCases.add(testCase(
                    "RUN-AI-POLICY-" + key.name(),
                    key.displayName() + " 실행 정책 확인",
                    PASS,
                    "RUNTIME_PROBE",
                    "Harness policy resolves to provider " + plan.provider().providerCode()
                            + " / model " + plan.modelId().name()));
            providerPlans.putIfAbsent(plan.provider().id(), plan);
        }

        if (providerPlans.isEmpty()) {
            runtimeCases.add(testCase(
                    "RUN-AI-PROVIDER-NONE",
                    "실제 provider 연결 평가 대상 확인",
                    WARN,
                    "RUNTIME_PROBE",
                    "No runnable harness policy selected a provider, so no external model probe was executed."));
        }

        for (var plan : providerPlans.values()) {
            var provider = plan.provider();
            if (fakeProvider(provider.providerCode(), provider.providerType())) {
                fakeProviderCount++;
                runtimeCases.add(testCase(
                        "RUN-AI-PROVIDER-" + provider.id(),
                        provider.displayName() + " provider 연결 평가",
                        WARN,
                        "RUNTIME_PROBE",
                        "Provider " + provider.providerCode() + " is treated as a development fake provider. "
                                + "No external model probe was executed."));
                continue;
            }
            var result = connectionTestService.testProvider(principal, provider.id());
            realProviderTestedCount++;
            if (result.success()) {
                runtimeCases.add(providerCase(provider.id(), provider.displayName(), PASS, result));
            } else {
                failedRealProviderCount++;
                runtimeCases.add(providerCase(provider.id(), provider.displayName(), FAILED, result));
            }
        }

        var runtimeGroup = group(
                "RUNTIME_AI_PROVIDER_PROBE",
                "Runtime AI provider probe",
                "EVALUATION",
                runtimeCases);
        var groups = new ArrayList<>(baseline.groups());
        groups.add(runtimeGroup);
        var tokenGroups = tokenControlService.tokenControlGroups();
        groups.addAll(tokenGroups);
        var realModelSignalStatus = realModelSignalStatus(realProviderTestedCount, failedRealProviderCount);
        var signals = new ArrayList<>(runtimeSignals(
                baseline.signals(),
                unavailableHarnessCount,
                fakeProviderCount,
                realProviderTestedCount,
                failedRealProviderCount,
                realModelSignalStatus));
        signals.addAll(tokenControlService.tokenControlSignals(tokenGroups));
        return summary(groups, signals, dataPolicy(realProviderTestedCount, fakeProviderCount));
    }

    private AiWorkerEvaluationCaseResponse providerCase(
            Long providerId,
            String displayName,
            String status,
            AiProviderConnectionTestResponse result
    ) {
        return testCase(
                "RUN-AI-PROVIDER-" + providerId,
                displayName + " provider 연결 평가",
                status,
                "PROVIDER_CONNECTION_TEST",
                result.providerCode() + " / " + result.modelName()
                        + " / " + result.status()
                        + " / " + result.latencyMs() + "ms"
                        + " / " + result.message());
    }

    private List<AiWorkerEvaluationSignalResponse> runtimeSignals(
            List<AiWorkerEvaluationSignalResponse> baselineSignals,
            int unavailableHarnessCount,
            int fakeProviderCount,
            int realProviderTestedCount,
            int failedRealProviderCount,
            String realModelSignalStatus
    ) {
        var signals = new ArrayList<AiWorkerEvaluationSignalResponse>();
        for (var signal : baselineSignals) {
            if ("REAL_MODEL_EVALUATION".equals(signal.signalKey())) {
                signals.add(signal(
                        signal.signalKey(),
                        signal.displayName(),
                        realModelSignalStatus,
                        signal.layer(),
                        realModelEvidence(realProviderTestedCount, failedRealProviderCount, fakeProviderCount)));
            } else {
                signals.add(signal);
            }
        }
        signals.add(signal(
                "RUNTIME_HARNESS_POLICY",
                "Harness policy runtime resolution",
                unavailableHarnessCount > 0 ? WARN : PASS,
                "EVALUATION",
                unavailableHarnessCount == 0
                        ? "Every configured harness policy resolved at runtime."
                        : unavailableHarnessCount + " harness policy item(s) were not runnable."));
        signals.add(signal(
                "RUNTIME_PROVIDER_CONNECTIVITY",
                "Runtime provider connectivity probe",
                providerConnectivityStatus(realProviderTestedCount, failedRealProviderCount, fakeProviderCount),
                "EVALUATION",
                providerConnectivityEvidence(realProviderTestedCount, failedRealProviderCount, fakeProviderCount)));
        return List.copyOf(signals);
    }

    private String realModelSignalStatus(int realProviderTestedCount, int failedRealProviderCount) {
        if (failedRealProviderCount > 0) {
            return FAILED;
        }
        return realProviderTestedCount > 0 ? PASS : WARN;
    }

    private String providerConnectivityStatus(int realProviderTestedCount, int failedRealProviderCount, int fakeProviderCount) {
        if (failedRealProviderCount > 0) {
            return FAILED;
        }
        if (realProviderTestedCount == 0 || fakeProviderCount > 0) {
            return WARN;
        }
        return PASS;
    }

    private String realModelEvidence(int realProviderTestedCount, int failedRealProviderCount, int fakeProviderCount) {
        if (failedRealProviderCount > 0) {
            return failedRealProviderCount + " real provider probe(s) failed.";
        }
        if (realProviderTestedCount == 0) {
            return fakeProviderCount > 0
                    ? "Only fake provider policy assignments were found. Real provider probe was not executed."
                    : "No runnable harness policy selected a real provider.";
        }
        return realProviderTestedCount + " real provider probe(s) succeeded.";
    }

    private String providerConnectivityEvidence(int realProviderTestedCount, int failedRealProviderCount, int fakeProviderCount) {
        return "Real providers tested: " + realProviderTestedCount
                + ", failed: " + failedRealProviderCount
                + ", fake providers skipped: " + fakeProviderCount + ".";
    }

    private String dataPolicy(int realProviderTestedCount, int fakeProviderCount) {
        return "Runtime probe resolves AI harness policies and can submit one compact connectivity test per unique real provider. "
                + "It does not run document/legal judgment evaluation and does not mutate business data. "
                + "Real providers tested: " + realProviderTestedCount + ", fake providers skipped: " + fakeProviderCount + ".";
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
                signals);
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

    private boolean fakeProvider(String providerCode, AiProviderType providerType) {
        var code = providerCode == null ? "" : providerCode.trim().toLowerCase(Locale.ROOT);
        return code.startsWith("fake-") || code.contains("-fake") || code.contains("fake");
    }

    private static int percent(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round((numerator * 100.0d) / denominator);
    }
}
