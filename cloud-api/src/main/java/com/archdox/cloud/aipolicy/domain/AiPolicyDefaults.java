package com.archdox.cloud.aipolicy.domain;

import java.math.BigDecimal;

public final class AiPolicyDefaults {
    public static final int OFFICE_MAX_OUTPUT_TOKENS = 2_000;
    public static final int OFFICE_DAILY_CALL_LIMIT = 100;
    public static final long OFFICE_MONTHLY_TOKEN_LIMIT = 2_000_000L;
    public static final int USER_DAILY_CALL_LIMIT = 30;
    public static final long USER_MONTHLY_TOKEN_LIMIT = 500_000L;

    public static final int HARNESS_MAX_OUTPUT_TOKENS = 1_200;
    public static final int HARNESS_DAILY_CALL_LIMIT = 30;
    public static final long HARNESS_MONTHLY_TOKEN_LIMIT = 500_000L;

    public static final BigDecimal NO_MONTHLY_BUDGET_AMOUNT = null;
    public static final String DEFAULT_BUDGET_CURRENCY = "USD";

    private AiPolicyDefaults() {
    }
}
