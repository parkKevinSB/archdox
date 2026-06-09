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
@Table(name = "ai_model_call_logs")
public class AiModelCallLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "call_id", nullable = false)
    private String callId;

    @Column(name = "office_id")
    private Long officeId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "provider_credential_id")
    private Long providerCredentialId;

    @Column(name = "provider_code", nullable = false)
    private String providerCode;

    @Column(name = "provider_type", nullable = false)
    private String providerType;

    @Column(name = "model_id", nullable = false)
    private String modelId;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "feature")
    private String feature;

    @Column(name = "workflow_type")
    private String workflowType;

    @Column(name = "workflow_key")
    private String workflowKey;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiModelCallLogStatus status;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "finish_reason")
    private String finishReason;

    @Column(name = "provider_response_id")
    private String providerResponseId;

    @Column(name = "error_type")
    private String errorType;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "pricing_rule_id")
    private Long pricingRuleId;

    @Column(name = "cost_currency")
    private String costCurrency;

    @Column(name = "estimated_input_cost")
    private BigDecimal estimatedInputCost;

    @Column(name = "estimated_output_cost")
    private BigDecimal estimatedOutputCost;

    @Column(name = "estimated_total_cost")
    private BigDecimal estimatedTotalCost;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "completed_at", nullable = false)
    private OffsetDateTime completedAt;

    protected AiModelCallLog() {
    }

    public AiModelCallLog(
            String callId,
            Long officeId,
            Long userId,
            Long providerCredentialId,
            String providerCode,
            String providerType,
            String modelId,
            String modelName,
            String feature,
            String workflowType,
            String workflowKey,
            String resourceType,
            String resourceId,
            AiModelCallLogStatus status,
            Integer inputTokens,
            Integer outputTokens,
            Long latencyMs,
            String finishReason,
            String providerResponseId,
            String errorType,
            String errorMessage,
            Long pricingRuleId,
            String costCurrency,
            BigDecimal estimatedInputCost,
            BigDecimal estimatedOutputCost,
            BigDecimal estimatedTotalCost,
            OffsetDateTime requestedAt,
            OffsetDateTime completedAt
    ) {
        this.callId = callId;
        this.officeId = officeId;
        this.userId = userId;
        this.providerCredentialId = providerCredentialId;
        this.providerCode = providerCode;
        this.providerType = providerType;
        this.modelId = modelId;
        this.modelName = modelName;
        this.feature = feature;
        this.workflowType = workflowType;
        this.workflowKey = workflowKey;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.status = status;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.finishReason = finishReason;
        this.providerResponseId = providerResponseId;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.pricingRuleId = pricingRuleId;
        this.costCurrency = costCurrency;
        this.estimatedInputCost = estimatedInputCost;
        this.estimatedOutputCost = estimatedOutputCost;
        this.estimatedTotalCost = estimatedTotalCost;
        this.requestedAt = requestedAt;
        this.completedAt = completedAt;
    }

    public Long id() {
        return id;
    }

    public String callId() {
        return callId;
    }

    public Long officeId() {
        return officeId;
    }

    public Long userId() {
        return userId;
    }

    public Long providerCredentialId() {
        return providerCredentialId;
    }

    public String providerCode() {
        return providerCode;
    }

    public String providerType() {
        return providerType;
    }

    public String modelId() {
        return modelId;
    }

    public String modelName() {
        return modelName;
    }

    public String feature() {
        return feature;
    }

    public String workflowType() {
        return workflowType;
    }

    public String workflowKey() {
        return workflowKey;
    }

    public String resourceType() {
        return resourceType;
    }

    public String resourceId() {
        return resourceId;
    }

    public AiModelCallLogStatus status() {
        return status;
    }

    public Integer inputTokens() {
        return inputTokens;
    }

    public Integer outputTokens() {
        return outputTokens;
    }

    public Long latencyMs() {
        return latencyMs;
    }

    public String finishReason() {
        return finishReason;
    }

    public String providerResponseId() {
        return providerResponseId;
    }

    public String errorType() {
        return errorType;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public Long pricingRuleId() {
        return pricingRuleId;
    }

    public String costCurrency() {
        return costCurrency;
    }

    public BigDecimal estimatedInputCost() {
        return estimatedInputCost;
    }

    public BigDecimal estimatedOutputCost() {
        return estimatedOutputCost;
    }

    public BigDecimal estimatedTotalCost() {
        return estimatedTotalCost;
    }

    public OffsetDateTime requestedAt() {
        return requestedAt;
    }

    public OffsetDateTime completedAt() {
        return completedAt;
    }
}
