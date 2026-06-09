package com.archdox.cloud.aipolicy.application;

import com.archdox.cloud.account.domain.UserAccount;
import com.archdox.cloud.account.infra.UserAccountRepository;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicy;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.aipolicy.domain.AiModelPricingRule;
import com.archdox.cloud.aipolicy.domain.AiModelPricingRuleStatus;
import com.archdox.cloud.aipolicy.domain.AiPolicyDefaults;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.domain.AiProviderCredentialStatus;
import com.archdox.cloud.aipolicy.domain.OfficeAiPolicy;
import com.archdox.cloud.aipolicy.dto.AiBudgetUsageSummaryResponse;
import com.archdox.cloud.aipolicy.dto.AiHarnessBudgetUsageResponse;
import com.archdox.cloud.aipolicy.dto.AiOfficeBudgetUsageResponse;
import com.archdox.cloud.aipolicy.dto.AiPricingCoverageResponse;
import com.archdox.cloud.aipolicy.dto.AiUserBudgetUsageResponse;
import com.archdox.cloud.aipolicy.infra.AiHarnessPolicyRepository;
import com.archdox.cloud.aipolicy.infra.AiModelCallLogRepository;
import com.archdox.cloud.aipolicy.infra.AiModelPricingRuleRepository;
import com.archdox.cloud.aipolicy.infra.AiProviderCredentialRepository;
import com.archdox.cloud.aipolicy.infra.OfficeAiPolicyRepository;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.domain.Office;
import com.archdox.cloud.office.infra.OfficeRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiBudgetUsageReadService {
    private static final int USER_USAGE_LIMIT = 100;

    private final AiModelCallLogRepository callLogRepository;
    private final OfficeAiPolicyRepository officePolicyRepository;
    private final AiHarnessPolicyRepository harnessPolicyRepository;
    private final AiProviderCredentialRepository providerRepository;
    private final AiModelPricingRuleRepository pricingRuleRepository;
    private final OfficeRepository officeRepository;
    private final UserAccountRepository userRepository;
    private final PlatformAdminService platformAdminService;

    public AiBudgetUsageReadService(
            AiModelCallLogRepository callLogRepository,
            OfficeAiPolicyRepository officePolicyRepository,
            AiHarnessPolicyRepository harnessPolicyRepository,
            AiProviderCredentialRepository providerRepository,
            AiModelPricingRuleRepository pricingRuleRepository,
            OfficeRepository officeRepository,
            UserAccountRepository userRepository,
            PlatformAdminService platformAdminService
    ) {
        this.callLogRepository = callLogRepository;
        this.officePolicyRepository = officePolicyRepository;
        this.harnessPolicyRepository = harnessPolicyRepository;
        this.providerRepository = providerRepository;
        this.pricingRuleRepository = pricingRuleRepository;
        this.officeRepository = officeRepository;
        this.userRepository = userRepository;
        this.platformAdminService = platformAdminService;
    }

    @Transactional(readOnly = true)
    public AiBudgetUsageSummaryResponse monthlySummary(UserPrincipal principal) {
        platformAdminService.requirePlatformAdmin(principal);
        var now = OffsetDateTime.now();
        var dayStart = now.toLocalDate().atStartOfDay(now.getOffset()).toOffsetDateTime();
        var dayEnd = dayStart.plusDays(1);
        var monthStart = YearMonth.from(now).atDay(1).atStartOfDay(now.getOffset()).toOffsetDateTime();
        var monthEnd = monthStart.plusMonths(1);

        var offices = officeRepository.findAll().stream()
                .sorted(Comparator.comparing(Office::id))
                .toList();
        var policiesByOfficeId = officePolicyRepository.findAll().stream()
                .collect(Collectors.toMap(OfficeAiPolicy::officeId, Function.identity()));
        var providersById = providerRepository.findAll().stream()
                .collect(Collectors.toMap(AiProviderCredential::id, Function.identity()));
        var officeRows = offices.stream()
                .map(office -> officeBudgetRow(
                        office,
                        policiesByOfficeId.get(office.id()),
                        dayStart,
                        dayEnd,
                        monthStart,
                        monthEnd))
                .toList();

        var harnessPoliciesByKey = harnessPolicyRepository.findAllByOrderByPolicyKeyAsc().stream()
                .collect(Collectors.toMap(AiHarnessPolicy::policyKey, Function.identity()));
        var harnessRows = java.util.Arrays.stream(AiHarnessPolicyKey.values())
                .map(key -> harnessBudgetRow(
                        key,
                        harnessPoliciesByKey.get(key),
                        providersById,
                        dayStart,
                        dayEnd,
                        monthStart,
                        monthEnd))
                .toList();

        var userRows = userBudgetRows(
                policiesByOfficeId,
                offices.stream().collect(Collectors.toMap(Office::id, Function.identity())),
                dayStart,
                dayEnd,
                monthStart,
                monthEnd);

        var pricingCoverage = pricingCoverage(providersById, harnessPoliciesByKey);
        var missingPricing = (int) pricingCoverage.stream().filter(row -> !row.configured()).count();

        return new AiBudgetUsageSummaryResponse(
                monthStart,
                monthEnd,
                AiPolicyDefaults.DEFAULT_BUDGET_CURRENCY,
                officeRows.size(),
                (int) officeRows.stream().filter(AiOfficeBudgetUsageResponse::budgetEnforcementEnabled).count(),
                harnessRows.size(),
                (int) harnessRows.stream().filter(AiHarnessBudgetUsageResponse::budgetEnforcementEnabled).count(),
                userRows.size(),
                missingPricing,
                officeRows,
                harnessRows,
                userRows,
                pricingCoverage);
    }

    private AiOfficeBudgetUsageResponse officeBudgetRow(
            Office office,
            OfficeAiPolicy policy,
            OffsetDateTime dayStart,
            OffsetDateTime dayEnd,
            OffsetDateTime monthStart,
            OffsetDateTime monthEnd
    ) {
        var currency = policy == null ? AiPolicyDefaults.DEFAULT_BUDGET_CURRENCY : currency(policy.budgetCurrency());
        var dailyCalls = callLogRepository.countByOfficeIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                office.id(),
                dayStart,
                dayEnd);
        var monthlyTokens = number(callLogRepository.sumTokensByOfficeIdAndCompletedAtRange(
                office.id(),
                monthStart,
                monthEnd));
        var monthlyCost = money(callLogRepository.sumEstimatedCostByOfficeIdAndCurrencyAndCompletedAtRange(
                office.id(),
                currency,
                monthStart,
                monthEnd));
        var aiEnabled = policy != null && policy.aiEnabled();
        var budgetEnabled = policy != null && policy.budgetEnforcementEnabled();
        var dailyLimit = policy == null ? AiPolicyDefaults.OFFICE_DAILY_CALL_LIMIT : policy.dailyCallLimit();
        var tokenLimit = policy == null ? AiPolicyDefaults.OFFICE_MONTHLY_TOKEN_LIMIT : policy.monthlyTokenLimit();
        var budgetAmount = policy == null ? null : policy.monthlyBudgetAmount();
        var status = strongest(
                policy == null ? "WARN" : "OK",
                aiEnabled ? "OK" : "DISABLED",
                budgetEnabled ? "OK" : "WARN",
                limitStatus(dailyCalls, dailyLimit),
                limitStatus(monthlyTokens, tokenLimit),
                moneyStatus(monthlyCost, budgetAmount));
        return new AiOfficeBudgetUsageResponse(
                office.id(),
                office.officeCode(),
                office.displayName(),
                aiEnabled,
                budgetEnabled,
                dailyLimit,
                dailyCalls,
                tokenLimit,
                monthlyTokens,
                policy == null ? AiPolicyDefaults.OFFICE_MAX_OUTPUT_TOKENS : policy.maxOutputTokens(),
                policy == null ? AiPolicyDefaults.USER_DAILY_CALL_LIMIT : policy.perUserDailyCallLimit(),
                policy == null ? AiPolicyDefaults.USER_MONTHLY_TOKEN_LIMIT : policy.perUserMonthlyTokenLimit(),
                budgetAmount,
                currency,
                monthlyCost,
                status,
                officeMessage(policy, status));
    }

    private AiHarnessBudgetUsageResponse harnessBudgetRow(
            AiHarnessPolicyKey key,
            AiHarnessPolicy policy,
            Map<Long, AiProviderCredential> providersById,
            OffsetDateTime dayStart,
            OffsetDateTime dayEnd,
            OffsetDateTime monthStart,
            OffsetDateTime monthEnd
    ) {
        var provider = policy == null || policy.providerCredentialId() == null
                ? null
                : providersById.get(policy.providerCredentialId());
        var providerCode = provider == null ? null : provider.providerCode();
        var modelName = effectiveModelName(policy, provider);
        var currency = policy == null ? AiPolicyDefaults.DEFAULT_BUDGET_CURRENCY : currency(policy.budgetCurrency());
        var dailyCalls = callLogRepository.countByFeatureAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                key.name(),
                dayStart,
                dayEnd);
        var monthlyTokens = number(callLogRepository.sumTokensByFeatureAndCompletedAtRange(
                key.name(),
                monthStart,
                monthEnd));
        var monthlyCost = money(callLogRepository.sumEstimatedCostByFeatureAndCurrencyAndCompletedAtRange(
                key.name(),
                currency,
                monthStart,
                monthEnd));
        var pricing = pricingMatch(providerCode, modelName);
        var effectiveEnabled = policy != null
                && policy.enabled()
                && provider != null
                && provider.status() == AiProviderCredentialStatus.ACTIVE
                && modelName != null
                && !modelName.isBlank();
        var dailyLimit = policy == null ? AiPolicyDefaults.HARNESS_DAILY_CALL_LIMIT : policy.dailyCallLimit();
        var tokenLimit = policy == null ? AiPolicyDefaults.HARNESS_MONTHLY_TOKEN_LIMIT : policy.monthlyTokenLimit();
        var budgetAmount = policy == null ? null : policy.monthlyBudgetAmount();
        var status = strongest(
                policy == null ? "WARN" : "OK",
                effectiveEnabled ? "OK" : "DISABLED",
                policy == null || policy.budgetEnforcementEnabled() ? "OK" : "WARN",
                limitStatus(dailyCalls, dailyLimit),
                limitStatus(monthlyTokens, tokenLimit),
                moneyStatus(monthlyCost, budgetAmount),
                budgetAmount == null || pricing.configured ? "OK" : "WARN");
        return new AiHarnessBudgetUsageResponse(
                key.name(),
                policy == null ? key.displayName() : policy.displayName(),
                effectiveEnabled,
                providerCode,
                modelName,
                policy == null ? AiPolicyDefaults.HARNESS_MAX_OUTPUT_TOKENS : policy.maxOutputTokens(),
                policy == null || policy.budgetEnforcementEnabled(),
                dailyLimit,
                dailyCalls,
                tokenLimit,
                monthlyTokens,
                budgetAmount,
                currency,
                monthlyCost,
                pricing.configured,
                status,
                harnessMessage(policy, effectiveEnabled, pricing, status));
    }

    private List<AiUserBudgetUsageResponse> userBudgetRows(
            Map<Long, OfficeAiPolicy> policiesByOfficeId,
            Map<Long, Office> officesById,
            OffsetDateTime dayStart,
            OffsetDateTime dayEnd,
            OffsetDateTime monthStart,
            OffsetDateTime monthEnd
    ) {
        var groups = callLogRepository.usageByOfficeAndUser(monthStart, monthEnd, PageRequest.of(0, USER_USAGE_LIMIT));
        var userIds = groups.stream()
                .map(group -> group.getUserId())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserAccount::id, Function.identity()));
        return groups.stream()
                .map(group -> {
                    var policy = policiesByOfficeId.get(group.getOfficeId());
                    var office = officesById.get(group.getOfficeId());
                    var user = usersById.get(group.getUserId());
                    var dailyLimit = policy == null
                            ? AiPolicyDefaults.USER_DAILY_CALL_LIMIT
                            : policy.perUserDailyCallLimit();
                    var tokenLimit = policy == null
                            ? AiPolicyDefaults.USER_MONTHLY_TOKEN_LIMIT
                            : policy.perUserMonthlyTokenLimit();
                    var dailyCalls = callLogRepository.countByOfficeIdAndUserIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                            group.getOfficeId(),
                            group.getUserId(),
                            dayStart,
                            dayEnd);
                    var monthlyTokens = number(group.getInputTokens()) + number(group.getOutputTokens());
                    var status = strongest(limitStatus(dailyCalls, dailyLimit), limitStatus(monthlyTokens, tokenLimit));
                    return new AiUserBudgetUsageResponse(
                            group.getOfficeId(),
                            office == null ? null : office.officeCode(),
                            group.getUserId(),
                            user == null ? null : user.email(),
                            user == null ? null : user.name(),
                            dailyLimit,
                            dailyCalls,
                            tokenLimit,
                            monthlyTokens,
                            status,
                            userMessage(status));
                })
                .toList();
    }

    private List<AiPricingCoverageResponse> pricingCoverage(
            Map<Long, AiProviderCredential> providersById,
            Map<AiHarnessPolicyKey, AiHarnessPolicy> harnessPoliciesByKey
    ) {
        var checks = new LinkedHashMap<String, PricingCheck>();
        providersById.values().stream()
                .filter(provider -> provider.status() == AiProviderCredentialStatus.ACTIVE)
                .filter(provider -> provider.defaultModel() != null && !provider.defaultModel().isBlank())
                .forEach(provider -> addPricingCheck(
                        checks,
                        "PROVIDER",
                        provider.providerCode(),
                        provider.providerCode(),
                        provider.defaultModel()));
        harnessPoliciesByKey.forEach((key, policy) -> {
            var provider = policy.providerCredentialId() == null ? null : providersById.get(policy.providerCredentialId());
            var modelName = effectiveModelName(policy, provider);
            addPricingCheck(
                    checks,
                    "HARNESS",
                    key.name(),
                    provider == null ? null : provider.providerCode(),
                    modelName);
        });
        return checks.values().stream()
                .map(check -> {
                    var match = pricingMatch(check.providerCode, check.modelName);
                    return new AiPricingCoverageResponse(
                            check.sourceType,
                            check.sourceKey,
                            check.providerCode,
                            check.modelName,
                            match.configured,
                            match.matchedBy,
                            match.ruleId,
                            match.configured ? "OK" : "WARN",
                            match.configured ? "Pricing rule is configured." : "Pricing rule is missing.");
                })
                .toList();
    }

    private void addPricingCheck(
            Map<String, PricingCheck> checks,
            String sourceType,
            String sourceKey,
            String providerCode,
            String modelName
    ) {
        if (providerCode == null || providerCode.isBlank() || modelName == null || modelName.isBlank()) {
            return;
        }
        var normalizedProvider = providerCode.trim().toLowerCase(Locale.ROOT);
        var normalizedModel = modelName.trim();
        checks.putIfAbsent(
                sourceType + ":" + sourceKey + ":" + normalizedProvider + ":" + normalizedModel,
                new PricingCheck(sourceType, sourceKey, normalizedProvider, normalizedModel));
    }

    private PricingMatch pricingMatch(String providerCode, String modelName) {
        if (providerCode == null || providerCode.isBlank() || modelName == null || modelName.isBlank()) {
            return PricingMatch.missing();
        }
        var normalizedProvider = providerCode.trim().toLowerCase(Locale.ROOT);
        var normalizedModel = modelName.trim();
        var exact = pricingRuleRepository.findFirstByProviderCodeAndModelNameAndStatusOrderByCreatedAtDesc(
                normalizedProvider,
                normalizedModel,
                AiModelPricingRuleStatus.ACTIVE);
        if (exact.isPresent()) {
            return PricingMatch.exact(exact.get());
        }
        return pricingRuleRepository.findFirstByProviderCodeAndModelNameAndStatusOrderByCreatedAtDesc(
                        normalizedProvider,
                        "*",
                        AiModelPricingRuleStatus.ACTIVE)
                .map(PricingMatch::wildcard)
                .orElseGet(PricingMatch::missing);
    }

    private String effectiveModelName(AiHarnessPolicy policy, AiProviderCredential provider) {
        if (policy != null && policy.modelName() != null && !policy.modelName().isBlank()) {
            return policy.modelName().trim();
        }
        return provider == null ? null : provider.defaultModel();
    }

    private String officeMessage(OfficeAiPolicy policy, String status) {
        if (policy == null) {
            return "Office AI policy is not configured.";
        }
        if (!policy.aiEnabled()) {
            return "AI is disabled for this office.";
        }
        if (!policy.budgetEnforcementEnabled()) {
            return "Budget guard is disabled for this office.";
        }
        return switch (status) {
            case "BLOCKED" -> "Office AI budget or token limit is exhausted.";
            case "WARN" -> "Office AI usage is near a configured limit.";
            default -> "Office AI usage is within configured limits.";
        };
    }

    private String harnessMessage(AiHarnessPolicy policy, boolean effectiveEnabled, PricingMatch pricing, String status) {
        if (policy == null) {
            return "Harness policy is not configured.";
        }
        if (!effectiveEnabled) {
            return "Harness is not executable with the current provider/model policy.";
        }
        if (policy.monthlyBudgetAmount() != null && !pricing.configured) {
            return "Monthly budget is configured but model pricing rule is missing.";
        }
        return switch (status) {
            case "BLOCKED" -> "Harness budget or token limit is exhausted.";
            case "WARN" -> "Harness usage is near a configured limit.";
            default -> "Harness usage is within configured limits.";
        };
    }

    private String userMessage(String status) {
        return switch (status) {
            case "BLOCKED" -> "User AI budget or token limit is exhausted.";
            case "WARN" -> "User AI usage is near a configured limit.";
            default -> "User AI usage is within configured limits.";
        };
    }

    private String limitStatus(long used, Number limit) {
        if (limit == null) {
            return "OK";
        }
        var limitValue = limit.longValue();
        if (limitValue <= 0) {
            return "BLOCKED";
        }
        if (used >= limitValue) {
            return "BLOCKED";
        }
        return used >= Math.ceil(limitValue * 0.8d) ? "WARN" : "OK";
    }

    private String moneyStatus(BigDecimal used, BigDecimal limit) {
        if (limit == null) {
            return "OK";
        }
        if (BigDecimal.ZERO.compareTo(limit) >= 0) {
            return "BLOCKED";
        }
        if (used.compareTo(limit) >= 0) {
            return "BLOCKED";
        }
        return used.compareTo(limit.multiply(new BigDecimal("0.8"))) >= 0 ? "WARN" : "OK";
    }

    private String strongest(String... statuses) {
        var strongest = "OK";
        for (var status : statuses) {
            if ("BLOCKED".equals(status)) {
                return "BLOCKED";
            }
            if ("WARN".equals(status)) {
                strongest = "WARN";
            } else if ("DISABLED".equals(status) && "OK".equals(strongest)) {
                strongest = "DISABLED";
            }
        }
        return strongest;
    }

    private String currency(String currency) {
        if (currency == null || currency.isBlank()) {
            return AiPolicyDefaults.DEFAULT_BUDGET_CURRENCY;
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private long number(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record PricingCheck(String sourceType, String sourceKey, String providerCode, String modelName) {
    }

    private record PricingMatch(boolean configured, String matchedBy, Long ruleId) {
        static PricingMatch exact(AiModelPricingRule rule) {
            return new PricingMatch(true, "EXACT", rule.id());
        }

        static PricingMatch wildcard(AiModelPricingRule rule) {
            return new PricingMatch(true, "WILDCARD", rule.id());
        }

        static PricingMatch missing() {
            return new PricingMatch(false, "MISSING", null);
        }
    }
}
