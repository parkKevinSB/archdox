package com.archdox.cloud.aipolicy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ai_harness_policies")
public class AiHarnessPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_key", nullable = false, unique = true)
    private AiHarnessPolicyKey policyKey;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "description")
    private String description;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "provider_credential_id")
    private Long providerCredentialId;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 2;

    @Column(name = "timeout_seconds", nullable = false)
    private long timeoutSeconds = 90;

    @Column(name = "max_output_tokens", nullable = false)
    private int maxOutputTokens = AiPolicyDefaults.HARNESS_MAX_OUTPUT_TOKENS;

    @Column(name = "budget_enforcement_enabled", nullable = false)
    private boolean budgetEnforcementEnabled = true;

    @Column(name = "monthly_budget_amount")
    private BigDecimal monthlyBudgetAmount = AiPolicyDefaults.NO_MONTHLY_BUDGET_AMOUNT;

    @Column(name = "budget_currency", nullable = false)
    private String budgetCurrency = AiPolicyDefaults.DEFAULT_BUDGET_CURRENCY;

    @Column(name = "daily_call_limit", nullable = false)
    private int dailyCallLimit = AiPolicyDefaults.HARNESS_DAILY_CALL_LIMIT;

    @Column(name = "monthly_token_limit", nullable = false)
    private long monthlyTokenLimit = AiPolicyDefaults.HARNESS_MONTHLY_TOKEN_LIMIT;

    @Column(name = "policy_version", nullable = false)
    private long policyVersion = 1;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AiHarnessPolicy() {
    }

    public AiHarnessPolicy(AiHarnessPolicyKey policyKey, Long updatedByUserId, OffsetDateTime now) {
        this.policyKey = policyKey;
        this.displayName = policyKey.displayName();
        this.description = policyKey.description();
        this.updatedByUserId = updatedByUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            Boolean enabled,
            Long providerCredentialId,
            String modelName,
            Integer maxAttempts,
            Long timeoutSeconds,
            Long updatedByUserId,
            OffsetDateTime now
    ) {
        update(
                enabled,
                providerCredentialId,
                modelName,
                maxAttempts,
                timeoutSeconds,
                null,
                null,
                null,
                null,
                null,
                null,
                updatedByUserId,
                now);
    }

    public void update(
            Boolean enabled,
            Long providerCredentialId,
            String modelName,
            Integer maxAttempts,
            Long timeoutSeconds,
            Integer maxOutputTokens,
            Boolean budgetEnforcementEnabled,
            BigDecimal monthlyBudgetAmount,
            String budgetCurrency,
            Integer dailyCallLimit,
            Long monthlyTokenLimit,
            Long updatedByUserId,
            OffsetDateTime now
    ) {
        if (enabled != null) {
            this.enabled = enabled;
        }
        this.providerCredentialId = providerCredentialId;
        this.modelName = blankToNull(modelName);
        if (maxAttempts != null) {
            this.maxAttempts = Math.max(1, maxAttempts);
        }
        if (timeoutSeconds != null) {
            this.timeoutSeconds = Math.max(10, timeoutSeconds);
        }
        if (maxOutputTokens != null) {
            this.maxOutputTokens = Math.max(1, maxOutputTokens);
        }
        if (budgetEnforcementEnabled != null) {
            this.budgetEnforcementEnabled = budgetEnforcementEnabled;
        }
        this.monthlyBudgetAmount = monthlyBudgetAmount;
        if (budgetCurrency != null && !budgetCurrency.isBlank()) {
            this.budgetCurrency = budgetCurrency.trim().toUpperCase(java.util.Locale.ROOT);
        }
        if (dailyCallLimit != null) {
            this.dailyCallLimit = Math.max(0, dailyCallLimit);
        }
        if (monthlyTokenLimit != null) {
            this.monthlyTokenLimit = Math.max(0L, monthlyTokenLimit);
        }
        this.displayName = policyKey.displayName();
        this.description = policyKey.description();
        this.updatedByUserId = updatedByUserId;
        this.policyVersion++;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public AiHarnessPolicyKey policyKey() {
        return policyKey;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public boolean enabled() {
        return enabled;
    }

    public Long providerCredentialId() {
        return providerCredentialId;
    }

    public String modelName() {
        return modelName;
    }

    public int maxAttempts() {
        return Math.max(1, maxAttempts);
    }

    public long timeoutSeconds() {
        return Math.max(10, timeoutSeconds);
    }

    public int maxOutputTokens() {
        return Math.max(1, maxOutputTokens);
    }

    public boolean budgetEnforcementEnabled() {
        return budgetEnforcementEnabled;
    }

    public BigDecimal monthlyBudgetAmount() {
        return monthlyBudgetAmount;
    }

    public String budgetCurrency() {
        return budgetCurrency == null || budgetCurrency.isBlank()
                ? AiPolicyDefaults.DEFAULT_BUDGET_CURRENCY
                : budgetCurrency;
    }

    public int dailyCallLimit() {
        return Math.max(0, dailyCallLimit);
    }

    public long monthlyTokenLimit() {
        return Math.max(0L, monthlyTokenLimit);
    }

    public long policyVersion() {
        return policyVersion;
    }

    public Long updatedByUserId() {
        return updatedByUserId;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
