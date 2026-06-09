package com.archdox.cloud.aiharness.application;

import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationCaseResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationGroupResponse;
import com.archdox.cloud.aiharness.dto.AiWorkerEvaluationSignalResponse;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.domain.AiModelCallLog;
import com.archdox.cloud.aipolicy.domain.AiModelCallLogStatus;
import com.archdox.cloud.aipolicy.domain.AiModelPricingRuleStatus;
import com.archdox.cloud.aipolicy.domain.AiProviderType;
import com.archdox.cloud.aipolicy.infra.AiModelCallLogRepository;
import com.archdox.cloud.aipolicy.infra.AiModelPricingRuleRepository;
import com.archdox.cloud.aipolicy.infra.OfficeAiPolicyRepository;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiWorkerEvaluationTokenControlService {
    static final String GROUP_KEY = "TOKEN_COST_CONTROL";

    private static final String PASS = "PASS";
    private static final String WARN = "WARN";
    private static final String FAILED = "FAILED";
    private static final int SAFE_MAX_ATTEMPTS = 3;
    private static final int FAIL_MAX_ATTEMPTS = 6;
    private static final long SAFE_TIMEOUT_SECONDS = 180;
    private static final long FAIL_TIMEOUT_SECONDS = 600;
    private static final int SAFE_MAX_OUTPUT_TOKENS = 4_000;
    private static final int FAIL_MAX_OUTPUT_TOKENS = 16_000;
    private static final int RECENT_LOG_LIMIT = 200;
    private static final int SINGLE_CALL_WARN_TOKENS = 50_000;
    private static final int SINGLE_CALL_FAIL_TOKENS = 100_000;
    private static final int FEATURE_CALLS_PER_HOUR_WARN = 30;
    private static final int FEATURE_CALLS_PER_HOUR_FAIL = 100;
    private static final long FEATURE_TOKENS_PER_HOUR_WARN = 500_000L;
    private static final long FEATURE_TOKENS_PER_HOUR_FAIL = 1_000_000L;

    private final AiHarnessPolicyExecutionService policyExecutionService;
    private final AiModelCallLogRepository callLogRepository;
    private final AiModelPricingRuleRepository pricingRuleRepository;
    private final OfficeAiPolicyRepository officePolicyRepository;

    public AiWorkerEvaluationTokenControlService(
            AiHarnessPolicyExecutionService policyExecutionService,
            AiModelCallLogRepository callLogRepository,
            AiModelPricingRuleRepository pricingRuleRepository,
            OfficeAiPolicyRepository officePolicyRepository
    ) {
        this.policyExecutionService = policyExecutionService;
        this.callLogRepository = callLogRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.officePolicyRepository = officePolicyRepository;
    }

    @Transactional(readOnly = true)
    public List<AiWorkerEvaluationGroupResponse> tokenControlGroups() {
        var now = OffsetDateTime.now();
        var recentLogs = callLogRepository.findAllByOrderByCompletedAtDesc(PageRequest.of(0, RECENT_LOG_LIMIT));
        var cases = List.of(
                harnessRetryBudgetCase(),
                pricingCoverageCase(),
                officeBudgetCoverageCase(),
                harnessBudgetCoverageCase(),
                monthlyUsageTelemetryCase(now, recentLogs),
                recentBurstCase(now, recentLogs));
        return List.of(group(GROUP_KEY, "AI token/cost control", "EVALUATION", cases));
    }

    public List<AiWorkerEvaluationSignalResponse> tokenControlSignals(List<AiWorkerEvaluationGroupResponse> groups) {
        var group = groups == null
                ? null
                : groups.stream().filter(item -> GROUP_KEY.equals(item.groupKey())).findFirst().orElse(null);
        if (group == null) {
            return List.of();
        }
        return List.of(signal(
                "TOKEN_COST_CONTROL",
                "Token and cost control",
                groupStatus(group),
                "EVALUATION",
                tokenControlEvidence(group)));
    }

    private AiWorkerEvaluationCaseResponse harnessRetryBudgetCase() {
        var runnableCount = 0;
        var warningCount = 0;
        var failedCount = 0;
        var evidence = new ArrayList<String>();
        for (var key : AiHarnessPolicyKey.values()) {
            var resolution = policyExecutionService.resolve(key);
            if (!resolution.runnable()) {
                evidence.add(key.name() + ": not runnable(" + resolution.unavailableReason() + ")");
                continue;
            }
            runnableCount++;
            var plan = resolution.plan();
            var timeoutSeconds = plan.timeout().toSeconds();
            if (plan.maxAttempts() >= FAIL_MAX_ATTEMPTS
                    || timeoutSeconds >= FAIL_TIMEOUT_SECONDS
                    || plan.maxOutputTokens() >= FAIL_MAX_OUTPUT_TOKENS) {
                failedCount++;
            } else if (plan.maxAttempts() > SAFE_MAX_ATTEMPTS
                    || timeoutSeconds > SAFE_TIMEOUT_SECONDS
                    || plan.maxOutputTokens() > SAFE_MAX_OUTPUT_TOKENS) {
                warningCount++;
            }
            evidence.add(key.name()
                    + ": provider=" + plan.provider().providerCode()
                    + ", model=" + plan.modelId().name()
                    + ", maxAttempts=" + plan.maxAttempts()
                    + ", timeoutSeconds=" + timeoutSeconds
                    + ", maxOutputTokens=" + plan.maxOutputTokens());
        }
        var status = failedCount > 0 ? FAILED : (warningCount > 0 || runnableCount == 0 ? WARN : PASS);
        return testCase(
                "RUN-TOKEN-001",
                "Harness retry and timeout budget",
                status,
                "TOKEN_USAGE_POLICY",
                "Runnable harnesses=" + runnableCount
                        + ", risky=" + warningCount
                        + ", unsafe=" + failedCount
                        + ". " + String.join(" | ", evidence));
    }

    private AiWorkerEvaluationCaseResponse pricingCoverageCase() {
        var realProviderCount = 0;
        var missingPricing = new ArrayList<String>();
        var fakeProviderCount = 0;
        for (var key : AiHarnessPolicyKey.values()) {
            var resolution = policyExecutionService.resolve(key);
            if (!resolution.runnable()) {
                continue;
            }
            var plan = resolution.plan();
            if (fakeProvider(plan.provider().providerCode(), plan.provider().providerType())) {
                fakeProviderCount++;
                continue;
            }
            realProviderCount++;
            if (!hasPricingRule(plan.provider().providerCode(), plan.modelId().name())) {
                missingPricing.add(plan.provider().providerCode() + "/" + plan.modelId().name());
            }
        }
        var status = missingPricing.isEmpty() && realProviderCount > 0 ? PASS : WARN;
        var evidence = realProviderCount == 0
                ? "No runnable real provider model was selected. fakeProviders=" + fakeProviderCount
                : "Real provider models=" + realProviderCount
                        + ", missingPricing=" + missingPricing.size()
                        + (missingPricing.isEmpty() ? "" : " -> " + String.join(", ", missingPricing));
        return testCase(
                "RUN-TOKEN-002",
                "Pricing rule coverage for active harness models",
                status,
                "TOKEN_COST_POLICY",
                evidence);
    }

    private AiWorkerEvaluationCaseResponse officeBudgetCoverageCase() {
        var policies = officePolicyRepository.findAll();
        var active = policies.stream().filter(policy -> policy.aiEnabled()).toList();
        var budgetEnabled = active.stream().filter(policy -> policy.budgetEnforcementEnabled()).toList();
        var dailyLimit = budgetEnabled.stream().filter(policy -> policy.dailyCallLimit() != null).count();
        var monthlyTokenLimit = budgetEnabled.stream().filter(policy -> policy.monthlyTokenLimit() != null).count();
        var monthlyBudget = budgetEnabled.stream().filter(policy -> policy.monthlyBudgetAmount() != null).count();
        var maxOutputTokenLimit = active.stream().filter(policy -> policy.maxOutputTokens() > 0).count();
        var perUserDailyLimit = budgetEnabled.stream().filter(policy -> policy.perUserDailyCallLimit() >= 0).count();
        var perUserMonthlyTokenLimit = budgetEnabled.stream().filter(policy -> policy.perUserMonthlyTokenLimit() >= 0).count();
        var status = PASS;
        if (active.isEmpty() || budgetEnabled.isEmpty()) {
            status = WARN;
        } else if (dailyLimit < budgetEnabled.size()
                || monthlyTokenLimit < budgetEnabled.size()
                || perUserDailyLimit < budgetEnabled.size()
                || perUserMonthlyTokenLimit < budgetEnabled.size()
                || maxOutputTokenLimit < active.size()) {
            status = WARN;
        }
        return testCase(
                "RUN-TOKEN-003",
                "Office and user AI budget guard coverage",
                status,
                "TOKEN_COST_POLICY",
                "activeOfficePolicies=" + active.size()
                        + ", budgetEnabled=" + budgetEnabled.size()
                        + ", dailyCallLimit=" + dailyLimit
                        + ", monthlyTokenLimit=" + monthlyTokenLimit
                        + ", maxOutputTokens=" + maxOutputTokenLimit
                        + ", perUserDailyCallLimit=" + perUserDailyLimit
                        + ", perUserMonthlyTokenLimit=" + perUserMonthlyTokenLimit
                        + ", monthlyBudget=" + monthlyBudget
                        + ". Office and user AI calls pass through AiBudgetGuardService before execution.");
    }

    private AiWorkerEvaluationCaseResponse harnessBudgetCoverageCase() {
        var runnableCount = 0;
        var budgetEnabledCount = 0;
        var dailyLimitCount = 0;
        var monthlyTokenLimitCount = 0;
        var monthlyBudgetCount = 0;
        var evidence = new ArrayList<String>();
        for (var key : AiHarnessPolicyKey.values()) {
            var resolution = policyExecutionService.resolve(key);
            if (!resolution.runnable()) {
                continue;
            }
            runnableCount++;
            var plan = resolution.plan();
            if (plan.budgetEnforcementEnabled()) {
                budgetEnabledCount++;
            }
            if (plan.dailyCallLimit() >= 0) {
                dailyLimitCount++;
            }
            if (plan.monthlyTokenLimit() >= 0) {
                monthlyTokenLimitCount++;
            }
            if (plan.monthlyBudgetAmount() != null) {
                monthlyBudgetCount++;
            }
            evidence.add(key.name()
                    + ": budget=" + plan.budgetEnforcementEnabled()
                    + ", dailyCallLimit=" + plan.dailyCallLimit()
                    + ", monthlyTokenLimit=" + plan.monthlyTokenLimit()
                    + ", monthlyBudget=" + (plan.monthlyBudgetAmount() == null ? "not-set" : plan.monthlyBudgetAmount()));
        }
        var status = PASS;
        if (runnableCount == 0 || budgetEnabledCount < runnableCount) {
            status = WARN;
        } else if (dailyLimitCount < runnableCount || monthlyTokenLimitCount < runnableCount) {
            status = WARN;
        }
        return testCase(
                "RUN-TOKEN-006",
                "Platform harness budget guard coverage",
                status,
                "TOKEN_COST_POLICY",
                "runnableHarnesses=" + runnableCount
                        + ", budgetEnabled=" + budgetEnabledCount
                        + ", dailyCallLimit=" + dailyLimitCount
                        + ", monthlyTokenLimit=" + monthlyTokenLimitCount
                        + ", monthlyBudget=" + monthlyBudgetCount
                        + ". " + String.join(" | ", evidence));
    }

    private AiWorkerEvaluationCaseResponse monthlyUsageTelemetryCase(
            OffsetDateTime now,
            List<AiModelCallLog> recentLogs
    ) {
        var from = YearMonth.from(now).atDay(1).atStartOfDay(now.getOffset()).toOffsetDateTime();
        var to = from.plusMonths(1);
        var groups = callLogRepository.usageByOfficeAndFeature(
                from,
                to,
                AiModelCallLogStatus.SUCCEEDED,
                AiModelCallLogStatus.FAILED);
        var totalCalls = groups.stream().mapToLong(group -> number(group.getCallCount())).sum();
        var inputTokens = groups.stream().mapToLong(group -> number(group.getInputTokens())).sum();
        var outputTokens = groups.stream().mapToLong(group -> number(group.getOutputTokens())).sum();
        var missingTokenLogs = recentLogs.stream()
                .filter(log -> log.status() == AiModelCallLogStatus.SUCCEEDED)
                .filter(log -> log.inputTokens() == null || log.outputTokens() == null)
                .count();
        var status = totalCalls == 0 || missingTokenLogs > 0 ? WARN : PASS;
        return testCase(
                "RUN-TOKEN-004",
                "AI usage telemetry and token accounting",
                status,
                "TOKEN_USAGE_TELEMETRY",
                "monthCalls=" + totalCalls
                        + ", inputTokens=" + inputTokens
                        + ", outputTokens=" + outputTokens
                        + ", recentSucceededLogsMissingTokens=" + missingTokenLogs
                        + ", usageGroups=" + groups.size());
    }

    private AiWorkerEvaluationCaseResponse recentBurstCase(
            OffsetDateTime now,
            List<AiModelCallLog> recentLogs
    ) {
        var recentFrom = now.minusHours(1);
        var recent = recentLogs.stream()
                .filter(log -> log.completedAt() != null && !log.completedAt().isBefore(recentFrom))
                .toList();
        var groups = new LinkedHashMap<String, RecentFeatureUsage>();
        var maxSingleCallTokens = 0L;
        for (var log : recent) {
            var tokens = tokens(log);
            maxSingleCallTokens = Math.max(maxSingleCallTokens, tokens);
            var key = feature(log);
            groups.computeIfAbsent(key, RecentFeatureUsage::new).add(tokens);
        }
        var hottest = groups.values().stream()
                .max(Comparator.comparingLong(RecentFeatureUsage::tokens)
                        .thenComparingInt(RecentFeatureUsage::calls))
                .orElse(null);
        var status = PASS;
        if (maxSingleCallTokens >= SINGLE_CALL_FAIL_TOKENS
                || (hottest != null && (hottest.calls() >= FEATURE_CALLS_PER_HOUR_FAIL
                || hottest.tokens() >= FEATURE_TOKENS_PER_HOUR_FAIL))) {
            status = FAILED;
        } else if (maxSingleCallTokens >= SINGLE_CALL_WARN_TOKENS
                || (hottest != null && (hottest.calls() >= FEATURE_CALLS_PER_HOUR_WARN
                || hottest.tokens() >= FEATURE_TOKENS_PER_HOUR_WARN))) {
            status = WARN;
        }
        return testCase(
                "RUN-TOKEN-005",
                "Recent token burst and repeat-call signal",
                status,
                "TOKEN_USAGE_TELEMETRY",
                hottest == null
                        ? "No AI model calls were logged in the last hour."
                        : "lastHourCalls=" + recent.size()
                        + ", hottestFeature=" + hottest.feature()
                        + ", hottestCalls=" + hottest.calls()
                        + ", hottestTokens=" + hottest.tokens()
                        + ", maxSingleCallTokens=" + maxSingleCallTokens);
    }

    private boolean hasPricingRule(String providerCode, String modelName) {
        var provider = normalizeProvider(providerCode);
        var model = modelName == null ? "" : modelName.trim();
        return pricingRuleRepository.existsByProviderCodeAndModelNameAndStatus(provider, model, AiModelPricingRuleStatus.ACTIVE)
                || pricingRuleRepository.existsByProviderCodeAndModelNameAndStatus(provider, "*", AiModelPricingRuleStatus.ACTIVE);
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

    private String tokenControlEvidence(AiWorkerEvaluationGroupResponse group) {
        if (group.failedCases() > 0) {
            return group.failedCases() + " token/cost control case(s) failed.";
        }
        if (group.warningCases() > 0) {
            return group.warningCases() + " token/cost control case(s) need review.";
        }
        return "Token/cost control cases passed.";
    }

    private boolean fakeProvider(String providerCode, AiProviderType providerType) {
        var code = normalizeProvider(providerCode);
        return code.startsWith("fake-") || code.contains("-fake") || code.contains("fake");
    }

    private String normalizeProvider(String providerCode) {
        return providerCode == null ? "" : providerCode.trim().toLowerCase(Locale.ROOT);
    }

    private long tokens(AiModelCallLog log) {
        return number(log.inputTokens()) + number(log.outputTokens());
    }

    private long number(Integer value) {
        return value == null ? 0L : value;
    }

    private long number(Long value) {
        return value == null ? 0L : value;
    }

    private String feature(AiModelCallLog log) {
        if (log.feature() != null && !log.feature().isBlank()) {
            return log.feature().trim();
        }
        return "UNKNOWN";
    }

    private static int percent(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round((numerator * 100.0d) / denominator);
    }

    private static final class RecentFeatureUsage {
        private final String feature;
        private int calls;
        private long tokens;

        private RecentFeatureUsage(String feature) {
            this.feature = feature;
        }

        private void add(long tokens) {
            this.calls++;
            this.tokens += tokens;
        }

        private String feature() {
            return feature;
        }

        private int calls() {
            return calls;
        }

        private long tokens() {
            return tokens;
        }
    }
}
