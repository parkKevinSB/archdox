package com.archdox.cloud.aipolicy.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AiUserBudgetOverrideResponse(
        Long id,
        Long officeId,
        String officeCode,
        String officeName,
        Long userId,
        String userEmail,
        String userName,
        Integer dailyCallLimit,
        Long monthlyTokenLimit,
        BigDecimal monthlyBudgetAmount,
        String budgetCurrency,
        String reason,
        boolean active,
        OffsetDateTime expiresAt,
        Long createdByUserId,
        OffsetDateTime createdAt,
        Long disabledByUserId,
        String disableReason,
        OffsetDateTime disabledAt
) {
}
