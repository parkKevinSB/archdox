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
@Table(name = "office_ai_policies")
public class OfficeAiPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false, unique = true)
    private Long officeId;

    @Column(name = "ai_enabled", nullable = false)
    private boolean aiEnabled;

    @Column(name = "document_review_ai_enabled", nullable = false)
    private boolean documentReviewAiEnabled;

    @Column(name = "document_generation_ai_enabled", nullable = false)
    private boolean documentGenerationAiEnabled;

    @Column(name = "preferred_provider_credential_id")
    private Long preferredProviderCredentialId;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_delivery_mode", nullable = false)
    private AiCredentialDeliveryMode credentialDeliveryMode = AiCredentialDeliveryMode.PROXY_ONLY;

    @Column(name = "budget_enforcement_enabled", nullable = false)
    private boolean budgetEnforcementEnabled;

    @Column(name = "monthly_budget_amount")
    private BigDecimal monthlyBudgetAmount;

    @Column(name = "budget_currency", nullable = false)
    private String budgetCurrency = "USD";

    @Column(name = "daily_call_limit")
    private Integer dailyCallLimit;

    @Column(name = "monthly_token_limit")
    private Long monthlyTokenLimit;

    @Column(name = "policy_version", nullable = false)
    private long policyVersion = 1;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected OfficeAiPolicy() {
    }

    public OfficeAiPolicy(Long officeId, Long updatedByUserId, OffsetDateTime now) {
        this.officeId = officeId;
        this.updatedByUserId = updatedByUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            Boolean aiEnabled,
            Boolean documentReviewAiEnabled,
            Boolean documentGenerationAiEnabled,
            Long preferredProviderCredentialId,
            AiCredentialDeliveryMode credentialDeliveryMode,
            Boolean budgetEnforcementEnabled,
            BigDecimal monthlyBudgetAmount,
            String budgetCurrency,
            Integer dailyCallLimit,
            Long monthlyTokenLimit,
            Long updatedByUserId,
            OffsetDateTime now
    ) {
        if (aiEnabled != null) {
            this.aiEnabled = aiEnabled;
        }
        if (documentReviewAiEnabled != null) {
            this.documentReviewAiEnabled = documentReviewAiEnabled;
        }
        if (documentGenerationAiEnabled != null) {
            this.documentGenerationAiEnabled = documentGenerationAiEnabled;
        }
        this.preferredProviderCredentialId = preferredProviderCredentialId;
        if (credentialDeliveryMode != null) {
            this.credentialDeliveryMode = credentialDeliveryMode;
        }
        if (budgetEnforcementEnabled != null) {
            this.budgetEnforcementEnabled = budgetEnforcementEnabled;
        }
        this.monthlyBudgetAmount = monthlyBudgetAmount;
        if (budgetCurrency != null && !budgetCurrency.isBlank()) {
            this.budgetCurrency = budgetCurrency;
        }
        this.dailyCallLimit = dailyCallLimit;
        this.monthlyTokenLimit = monthlyTokenLimit;
        this.updatedByUserId = updatedByUserId;
        this.policyVersion++;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public boolean aiEnabled() {
        return aiEnabled;
    }

    public boolean documentReviewAiEnabled() {
        return documentReviewAiEnabled;
    }

    public boolean documentGenerationAiEnabled() {
        return documentGenerationAiEnabled;
    }

    public Long preferredProviderCredentialId() {
        return preferredProviderCredentialId;
    }

    public AiCredentialDeliveryMode credentialDeliveryMode() {
        return credentialDeliveryMode;
    }

    public boolean budgetEnforcementEnabled() {
        return budgetEnforcementEnabled;
    }

    public BigDecimal monthlyBudgetAmount() {
        return monthlyBudgetAmount;
    }

    public String budgetCurrency() {
        return budgetCurrency;
    }

    public Integer dailyCallLimit() {
        return dailyCallLimit;
    }

    public Long monthlyTokenLimit() {
        return monthlyTokenLimit;
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
}
