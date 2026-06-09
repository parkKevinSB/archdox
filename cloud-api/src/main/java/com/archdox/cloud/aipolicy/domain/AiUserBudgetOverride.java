package com.archdox.cloud.aipolicy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Locale;

@Entity
@Table(name = "ai_user_budget_overrides")
public class AiUserBudgetOverride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "daily_call_limit")
    private Integer dailyCallLimit;

    @Column(name = "monthly_token_limit")
    private Long monthlyTokenLimit;

    @Column(name = "monthly_budget_amount")
    private BigDecimal monthlyBudgetAmount;

    @Column(name = "budget_currency", nullable = false)
    private String budgetCurrency = AiPolicyDefaults.DEFAULT_BUDGET_CURRENCY;

    @Column(nullable = false)
    private String reason;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "disabled_by_user_id")
    private Long disabledByUserId;

    @Column(name = "disable_reason")
    private String disableReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "disabled_at")
    private OffsetDateTime disabledAt;

    protected AiUserBudgetOverride() {
    }

    public AiUserBudgetOverride(
            Long officeId,
            Long userId,
            Integer dailyCallLimit,
            Long monthlyTokenLimit,
            BigDecimal monthlyBudgetAmount,
            String budgetCurrency,
            String reason,
            OffsetDateTime expiresAt,
            Long createdByUserId,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.userId = userId;
        this.dailyCallLimit = dailyCallLimit;
        this.monthlyTokenLimit = monthlyTokenLimit;
        this.monthlyBudgetAmount = monthlyBudgetAmount;
        this.budgetCurrency = budgetCurrency(budgetCurrency);
        this.reason = reason;
        this.expiresAt = expiresAt;
        this.createdByUserId = createdByUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void disable(Long disabledByUserId, String disableReason, OffsetDateTime now) {
        if (disabledAt != null) {
            return;
        }
        this.disabledByUserId = disabledByUserId;
        this.disableReason = blankToNull(disableReason);
        this.disabledAt = now;
        this.updatedAt = now;
    }

    public boolean activeAt(OffsetDateTime now) {
        return disabledAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public Long userId() {
        return userId;
    }

    public Integer dailyCallLimit() {
        return dailyCallLimit;
    }

    public Long monthlyTokenLimit() {
        return monthlyTokenLimit;
    }

    public BigDecimal monthlyBudgetAmount() {
        return monthlyBudgetAmount;
    }

    public String budgetCurrency() {
        return budgetCurrency == null || budgetCurrency.isBlank()
                ? AiPolicyDefaults.DEFAULT_BUDGET_CURRENCY
                : budgetCurrency;
    }

    public String reason() {
        return reason;
    }

    public OffsetDateTime expiresAt() {
        return expiresAt;
    }

    public Long createdByUserId() {
        return createdByUserId;
    }

    public Long disabledByUserId() {
        return disabledByUserId;
    }

    public String disableReason() {
        return disableReason;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }

    public OffsetDateTime disabledAt() {
        return disabledAt;
    }

    private String budgetCurrency(String value) {
        if (value == null || value.isBlank()) {
            return AiPolicyDefaults.DEFAULT_BUDGET_CURRENCY;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
