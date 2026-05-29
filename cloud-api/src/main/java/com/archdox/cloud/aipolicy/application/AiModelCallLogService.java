package com.archdox.cloud.aipolicy.application;

import com.archdox.cloud.aipolicy.domain.AiModelCallLog;
import com.archdox.cloud.aipolicy.domain.AiModelCallLogStatus;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.dto.AiModelCallLogResponse;
import com.archdox.cloud.aipolicy.infra.AiModelCallLogRepository;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiModelCallLogService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final AiModelCallLogRepository repository;
    private final AiModelPricingRuleService pricingRuleService;
    private final PlatformAdminService platformAdminService;

    public AiModelCallLogService(
            AiModelCallLogRepository repository,
            AiModelPricingRuleService pricingRuleService,
            PlatformAdminService platformAdminService
    ) {
        this.repository = repository;
        this.pricingRuleService = pricingRuleService;
        this.platformAdminService = platformAdminService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(
            String callId,
            AiProviderCredential provider,
            AiModelRequest request,
            AiModelResponse response,
            OffsetDateTime requestedAt,
            OffsetDateTime completedAt
    ) {
        var inputTokens = response.metadata().inputTokens().orElse(null);
        var outputTokens = response.metadata().outputTokens().orElse(null);
        var costEstimate = pricingRuleService.estimate(
                providerCode(provider, request),
                request.modelId().name(),
                inputTokens,
                outputTokens);
        repository.save(new AiModelCallLog(
                bounded(callId, 160),
                officeId(request),
                provider == null ? null : provider.id(),
                providerCode(provider, request),
                providerType(provider),
                bounded(request.modelId().asString(), 240),
                bounded(request.modelId().name(), 160),
                textOption(request, AiModelCallMetadata.FEATURE, 80),
                textOption(request, AiModelCallMetadata.WORKFLOW_TYPE, 120),
                textOption(request, AiModelCallMetadata.WORKFLOW_KEY, 240),
                textOption(request, AiModelCallMetadata.RESOURCE_TYPE, 120),
                textOption(request, AiModelCallMetadata.RESOURCE_ID, 120),
                AiModelCallLogStatus.SUCCEEDED,
                inputTokens,
                outputTokens,
                response.metadata().latency().map(java.time.Duration::toMillis).orElse(null),
                response.metadata().finishReason().map(value -> bounded(value, 80)).orElse(null),
                bounded(response.metadata().providerTrace().get("providerResponseId"), 160),
                null,
                null,
                costEstimate.map(AiModelCostEstimate::pricingRuleId).orElse(null),
                costEstimate.map(AiModelCostEstimate::currency).orElse(null),
                costEstimate.map(AiModelCostEstimate::inputCost).orElse(null),
                costEstimate.map(AiModelCostEstimate::outputCost).orElse(null),
                costEstimate.map(AiModelCostEstimate::totalCost).orElse(null),
                requestedAt,
                completedAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            String callId,
            AiProviderCredential provider,
            AiModelRequest request,
            Throwable error,
            OffsetDateTime requestedAt,
            OffsetDateTime completedAt
    ) {
        repository.save(new AiModelCallLog(
                bounded(callId, 160),
                officeId(request),
                provider == null ? null : provider.id(),
                providerCode(provider, request),
                providerType(provider),
                bounded(request.modelId().asString(), 240),
                bounded(request.modelId().name(), 160),
                textOption(request, AiModelCallMetadata.FEATURE, 80),
                textOption(request, AiModelCallMetadata.WORKFLOW_TYPE, 120),
                textOption(request, AiModelCallMetadata.WORKFLOW_KEY, 240),
                textOption(request, AiModelCallMetadata.RESOURCE_TYPE, 120),
                textOption(request, AiModelCallMetadata.RESOURCE_ID, 120),
                AiModelCallLogStatus.FAILED,
                null,
                null,
                null,
                null,
                null,
                bounded(error.getClass().getSimpleName(), 160),
                bounded(error.getMessage(), 4000),
                null,
                null,
                null,
                null,
                null,
                requestedAt,
                completedAt));
    }

    @Transactional(readOnly = true)
    public List<AiModelCallLogResponse> callLogs(UserPrincipal principal, Integer limit, String status) {
        platformAdminService.requirePlatformAdmin(principal);
        var page = PageRequest.of(0, boundedLimit(limit));
        var normalizedStatus = status(status);
        var logs = normalizedStatus == null
                ? repository.findAllByOrderByCompletedAtDesc(page)
                : repository.findByStatusOrderByCompletedAtDesc(normalizedStatus, page);
        return logs.stream().map(this::toResponse).toList();
    }

    private AiModelCallLogResponse toResponse(AiModelCallLog log) {
        return new AiModelCallLogResponse(
                log.id(),
                log.callId(),
                log.officeId(),
                log.providerCredentialId(),
                log.providerCode(),
                log.providerType(),
                log.modelId(),
                log.modelName(),
                log.feature(),
                log.workflowType(),
                log.workflowKey(),
                log.resourceType(),
                log.resourceId(),
                log.status().name(),
                log.inputTokens(),
                log.outputTokens(),
                log.latencyMs(),
                log.finishReason(),
                log.providerResponseId(),
                log.errorType(),
                log.errorMessage(),
                log.pricingRuleId(),
                log.costCurrency(),
                log.estimatedInputCost(),
                log.estimatedOutputCost(),
                log.estimatedTotalCost(),
                log.requestedAt(),
                log.completedAt());
    }

    private Long officeId(AiModelRequest request) {
        return request.options().get(AiModelCallMetadata.OFFICE_ID)
                .map(this::asLong)
                .orElse(null);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Long.valueOf(stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private AiModelCallLogStatus status(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return AiModelCallLogStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid AI call log status");
        }
    }

    private String textOption(AiModelRequest request, String key, int maxLength) {
        return request.options().get(key)
                .map(String::valueOf)
                .map(value -> bounded(value, maxLength))
                .orElse(null);
    }

    private String providerCode(AiProviderCredential provider, AiModelRequest request) {
        if (provider != null) {
            return bounded(provider.providerCode(), 120);
        }
        return bounded(request.modelId().provider(), 120);
    }

    private String providerType(AiProviderCredential provider) {
        if (provider == null || provider.providerType() == null) {
            return "UNKNOWN";
        }
        return bounded(provider.providerType().name(), 40);
    }

    private int boundedLimit(Integer limit) {
        return Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));
    }

    private String bounded(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
