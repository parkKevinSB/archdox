package com.archdox.cloud.aipolicy.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AiBudgetUsageSummaryResponse(
        OffsetDateTime periodFrom,
        OffsetDateTime periodTo,
        String currency,
        int officePolicyCount,
        int officesWithBudgetGuard,
        int harnessPolicyCount,
        int harnessesWithBudgetGuard,
        int userUsageCount,
        int activeUserOverrideCount,
        int missingPricingRuleCount,
        List<AiOfficeBudgetUsageResponse> offices,
        List<AiHarnessBudgetUsageResponse> harnesses,
        List<AiUserBudgetUsageResponse> users,
        List<AiPricingCoverageResponse> pricingCoverage
) {
}
