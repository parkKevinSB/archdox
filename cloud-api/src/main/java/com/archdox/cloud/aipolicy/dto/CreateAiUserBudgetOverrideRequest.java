package com.archdox.cloud.aipolicy.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateAiUserBudgetOverrideRequest(
        Long officeId,
        Long userId,
        Integer dailyCallLimit,
        Long monthlyTokenLimit,
        BigDecimal monthlyBudgetAmount,
        String budgetCurrency,
        OffsetDateTime expiresAt,
        String reason
) {
}
