package com.archdox.cloud.aipolicy.application;

import com.archdox.cloud.aipolicy.domain.AiModelPricingRuleStatus;
import com.archdox.cloud.aipolicy.domain.OfficeAiPolicy;
import com.archdox.cloud.aipolicy.infra.AiModelCallLogRepository;
import com.archdox.cloud.aipolicy.infra.AiModelPricingRuleRepository;
import com.archdox.cloud.global.api.BadRequestException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class AiBudgetGuardService {
    private final AiModelCallLogRepository callLogRepository;
    private final AiModelPricingRuleRepository pricingRuleRepository;

    public AiBudgetGuardService(
            AiModelCallLogRepository callLogRepository,
            AiModelPricingRuleRepository pricingRuleRepository
    ) {
        this.callLogRepository = callLogRepository;
        this.pricingRuleRepository = pricingRuleRepository;
    }

    public void requireWithinBudget(OfficeAiPolicy policy, String providerCode, String modelName, OffsetDateTime now) {
        if (policy == null || !policy.budgetEnforcementEnabled()) {
            return;
        }
        var dayStart = now.toLocalDate().atStartOfDay(now.getOffset()).toOffsetDateTime();
        var dayEnd = dayStart.plusDays(1);
        var monthStart = YearMonth.from(now).atDay(1).atStartOfDay(now.getOffset()).toOffsetDateTime();
        var monthEnd = monthStart.plusMonths(1);

        if (policy.dailyCallLimit() != null && policy.dailyCallLimit() >= 0) {
            var dailyCalls = callLogRepository.countByOfficeIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                    policy.officeId(),
                    dayStart,
                    dayEnd);
            if (dailyCalls >= policy.dailyCallLimit()) {
                throw budgetExceeded("Daily AI call limit exceeded for this office");
            }
        }

        if (policy.monthlyTokenLimit() != null && policy.monthlyTokenLimit() >= 0) {
            var monthlyTokens = callLogRepository.sumTokensByOfficeIdAndCompletedAtRange(
                    policy.officeId(),
                    monthStart,
                    monthEnd);
            if (monthlyTokens != null && monthlyTokens >= policy.monthlyTokenLimit()) {
                throw budgetExceeded("Monthly AI token limit exceeded for this office");
            }
        }

        if (policy.monthlyBudgetAmount() != null) {
            if (!hasPricingRule(providerCode, modelName)) {
                throw budgetExceeded("AI pricing rule is required before budget-enforced execution");
            }
            var monthlyCost = callLogRepository.sumEstimatedCostByOfficeIdAndCurrencyAndCompletedAtRange(
                    policy.officeId(),
                    currency(policy.budgetCurrency()),
                    monthStart,
                    monthEnd);
            if (monthlyCost != null && monthlyCost.compareTo(policy.monthlyBudgetAmount()) >= 0) {
                throw budgetExceeded("Monthly AI budget exceeded for this office");
            }
        }
    }

    private boolean hasPricingRule(String providerCode, String modelName) {
        var provider = providerCode == null ? "" : providerCode.trim().toLowerCase(Locale.ROOT);
        var model = modelName == null ? "" : modelName.trim();
        return pricingRuleRepository.existsByProviderCodeAndModelNameAndStatus(provider, model, AiModelPricingRuleStatus.ACTIVE)
                || pricingRuleRepository.existsByProviderCodeAndModelNameAndStatus(provider, "*", AiModelPricingRuleStatus.ACTIVE);
    }

    private String currency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "USD";
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private BadRequestException budgetExceeded(String message) {
        return new BadRequestException(
                "AI_BUDGET_EXCEEDED",
                "error.ai.budgetExceeded",
                message);
    }
}
