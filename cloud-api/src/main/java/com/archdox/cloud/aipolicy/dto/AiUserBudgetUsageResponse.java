package com.archdox.cloud.aipolicy.dto;

public record AiUserBudgetUsageResponse(
        Long officeId,
        String officeCode,
        Long userId,
        String userEmail,
        String userName,
        Integer dailyCallLimit,
        long dailyCallCount,
        Long monthlyTokenLimit,
        long monthlyTokens,
        String status,
        String message
) {
}
