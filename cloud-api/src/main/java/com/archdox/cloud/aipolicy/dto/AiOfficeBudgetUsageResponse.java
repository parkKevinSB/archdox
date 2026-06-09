package com.archdox.cloud.aipolicy.dto;

import java.math.BigDecimal;

public record AiOfficeBudgetUsageResponse(
        Long officeId,
        String officeCode,
        String officeName,
        boolean aiEnabled,
        boolean budgetEnforcementEnabled,
        Integer dailyCallLimit,
        long dailyCallCount,
        Long monthlyTokenLimit,
        long monthlyTokens,
        Integer maxOutputTokens,
        Integer perUserDailyCallLimit,
        Long perUserMonthlyTokenLimit,
        BigDecimal monthlyBudgetAmount,
        String budgetCurrency,
        BigDecimal monthlyEstimatedCost,
        String status,
        String message
) {
}
