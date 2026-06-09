package com.archdox.cloud.aipolicy.application;

import com.archdox.cloud.aipolicy.domain.AiModelPricingRule;
import com.archdox.cloud.aipolicy.domain.AiModelPricingRuleStatus;
import com.archdox.cloud.aipolicy.dto.AiModelPricingRuleResponse;
import com.archdox.cloud.aipolicy.dto.CreateAiModelPricingRuleRequest;
import com.archdox.cloud.aipolicy.infra.AiModelPricingRuleRepository;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiModelPricingRuleService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final int COST_SCALE = 8;

    private final AiModelPricingRuleRepository repository;
    private final PlatformAdminService platformAdminService;
    private final OperationEventService operationEventService;

    public AiModelPricingRuleService(
            AiModelPricingRuleRepository repository,
            PlatformAdminService platformAdminService,
            OperationEventService operationEventService
    ) {
        this.repository = repository;
        this.platformAdminService = platformAdminService;
        this.operationEventService = operationEventService;
    }

    @Transactional(readOnly = true)
    public List<AiModelPricingRuleResponse> pricingRules(UserPrincipal principal, Integer limit, String status) {
        platformAdminService.requirePlatformAdmin(principal);
        var page = PageRequest.of(0, boundedLimit(limit));
        var normalizedStatus = status(status);
        var rules = normalizedStatus == null
                ? repository.findAllByOrderByProviderCodeAscModelNameAscCreatedAtDesc(page)
                : repository.findByStatusOrderByProviderCodeAscModelNameAscCreatedAtDesc(normalizedStatus, page);
        return rules.stream().map(this::toResponse).toList();
    }

    @Transactional
    public AiModelPricingRuleResponse createPricingRule(UserPrincipal principal, CreateAiModelPricingRuleRequest request) {
        platformAdminService.requirePlatformAdmin(principal);
        var now = OffsetDateTime.now();
        var providerCode = providerCode(request.providerCode());
        var modelName = modelName(request.modelName());
        var currency = currency(request.currency());
        repository.findByProviderCodeAndModelNameAndStatusOrderByCreatedAtDesc(
                        providerCode,
                        modelName,
                        AiModelPricingRuleStatus.ACTIVE)
                .forEach(existing -> existing.disable(now));
        var rule = repository.save(new AiModelPricingRule(
                providerCode,
                modelName,
                currency,
                price(request.inputTokenPricePerMillion(), "inputTokenPricePerMillion"),
                price(request.outputTokenPricePerMillion(), "outputTokenPricePerMillion"),
                principal.userId(),
                now));
        operationEventService.record(
                null,
                OperationEventSeverity.INFO,
                "AI_MODEL_PRICING_RULE_CREATED",
                "ai-pricing-management",
                "ai-pricing-rule:" + rule.id(),
                "AI_MODEL_PRICING_RULE",
                rule.id(),
                principal.userId(),
                null,
                "AI model pricing rule created.",
                Map.of(
                        "pricingRuleId", rule.id(),
                        "providerCode", rule.providerCode(),
                        "modelName", rule.modelName(),
                        "currency", rule.currency(),
                        "inputTokenPricePerMillion", rule.inputTokenPricePerMillion().toPlainString(),
                        "outputTokenPricePerMillion", rule.outputTokenPricePerMillion().toPlainString()));
        return toResponse(rule);
    }

    @Transactional
    public AiModelPricingRuleResponse disablePricingRule(UserPrincipal principal, Long pricingRuleId) {
        platformAdminService.requirePlatformAdmin(principal);
        var rule = repository.findById(pricingRuleId)
                .orElseThrow(() -> new NotFoundException("AI model pricing rule not found"));
        if (rule.status() != AiModelPricingRuleStatus.DISABLED) {
            rule.disable(OffsetDateTime.now());
            operationEventService.record(
                    null,
                    OperationEventSeverity.INFO,
                    "AI_MODEL_PRICING_RULE_DISABLED",
                    "ai-pricing-management",
                    "ai-pricing-rule:" + rule.id(),
                    "AI_MODEL_PRICING_RULE",
                    rule.id(),
                    principal.userId(),
                    null,
                    "AI model pricing rule disabled.",
                    Map.of(
                            "pricingRuleId", rule.id(),
                            "providerCode", rule.providerCode(),
                            "modelName", rule.modelName()));
        }
        return toResponse(rule);
    }

    @Transactional(readOnly = true)
    public Optional<AiModelCostEstimate> estimate(
            String providerCode,
            String modelName,
            Integer inputTokens,
            Integer outputTokens
    ) {
        if ((inputTokens == null || inputTokens <= 0) && (outputTokens == null || outputTokens <= 0)) {
            return Optional.empty();
        }
        var normalizedProvider = providerCode(providerCode);
        var normalizedModel = modelName(modelName);
        var rule = repository.findFirstByProviderCodeAndModelNameAndStatusOrderByCreatedAtDesc(
                        normalizedProvider,
                        normalizedModel,
                        AiModelPricingRuleStatus.ACTIVE)
                .or(() -> repository.findFirstByProviderCodeAndModelNameAndStatusOrderByCreatedAtDesc(
                        normalizedProvider,
                        "*",
                        AiModelPricingRuleStatus.ACTIVE));
        return rule.map(value -> {
            var inputCost = cost(inputTokens, value.inputTokenPricePerMillion());
            var outputCost = cost(outputTokens, value.outputTokenPricePerMillion());
            return new AiModelCostEstimate(
                    value.id(),
                    value.currency(),
                    inputCost,
                    outputCost,
                    inputCost.add(outputCost).setScale(COST_SCALE, RoundingMode.HALF_UP));
        });
    }

    private AiModelPricingRuleResponse toResponse(AiModelPricingRule rule) {
        return new AiModelPricingRuleResponse(
                rule.id(),
                rule.providerCode(),
                rule.modelName(),
                rule.currency(),
                rule.inputTokenPricePerMillion(),
                rule.outputTokenPricePerMillion(),
                rule.status().name(),
                rule.createdByUserId(),
                rule.createdAt(),
                rule.updatedAt(),
                rule.disabledAt());
    }

    private BigDecimal cost(Integer tokens, BigDecimal pricePerMillion) {
        if (tokens == null || tokens <= 0) {
            return BigDecimal.ZERO.setScale(COST_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(tokens)
                .multiply(pricePerMillion)
                .divide(ONE_MILLION, COST_SCALE, RoundingMode.HALF_UP);
    }

    private AiModelPricingRuleStatus status(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AiModelPricingRuleStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid AI model pricing rule status");
        }
    }

    private String providerCode(String value) {
        var normalized = required(value, "providerCode").toLowerCase(Locale.ROOT);
        if (normalized.length() > 120) {
            throw new BadRequestException("providerCode is too long");
        }
        return normalized;
    }

    private String modelName(String value) {
        var normalized = required(value, "modelName");
        if (normalized.length() > 160) {
            throw new BadRequestException("modelName is too long");
        }
        return normalized;
    }

    private String currency(String value) {
        var normalized = value == null || value.isBlank() ? "USD" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 12) {
            throw new BadRequestException("currency is too long");
        }
        return normalized;
    }

    private BigDecimal price(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        if (value.signum() < 0) {
            throw new BadRequestException(fieldName + " must be greater than or equal to 0");
        }
        return value.setScale(COST_SCALE, RoundingMode.HALF_UP);
    }

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(fieldName + " is required");
        }
        return value.trim();
    }

    private int boundedLimit(Integer limit) {
        return Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));
    }
}
