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
@Table(name = "ai_model_pricing_rules")
public class AiModelPricingRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_code", nullable = false)
    private String providerCode;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(nullable = false)
    private String currency;

    @Column(name = "input_token_price_per_million", nullable = false)
    private BigDecimal inputTokenPricePerMillion;

    @Column(name = "output_token_price_per_million", nullable = false)
    private BigDecimal outputTokenPricePerMillion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiModelPricingRuleStatus status;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "disabled_at")
    private OffsetDateTime disabledAt;

    protected AiModelPricingRule() {
    }

    public AiModelPricingRule(
            String providerCode,
            String modelName,
            String currency,
            BigDecimal inputTokenPricePerMillion,
            BigDecimal outputTokenPricePerMillion,
            Long createdByUserId,
            OffsetDateTime now
    ) {
        this.providerCode = providerCode;
        this.modelName = modelName;
        this.currency = currency;
        this.inputTokenPricePerMillion = inputTokenPricePerMillion;
        this.outputTokenPricePerMillion = outputTokenPricePerMillion;
        this.status = AiModelPricingRuleStatus.ACTIVE;
        this.createdByUserId = createdByUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void disable(OffsetDateTime now) {
        this.status = AiModelPricingRuleStatus.DISABLED;
        this.disabledAt = now;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public String providerCode() {
        return providerCode;
    }

    public String modelName() {
        return modelName;
    }

    public String currency() {
        return currency;
    }

    public BigDecimal inputTokenPricePerMillion() {
        return inputTokenPricePerMillion;
    }

    public BigDecimal outputTokenPricePerMillion() {
        return outputTokenPricePerMillion;
    }

    public AiModelPricingRuleStatus status() {
        return status;
    }

    public Long createdByUserId() {
        return createdByUserId;
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
}
