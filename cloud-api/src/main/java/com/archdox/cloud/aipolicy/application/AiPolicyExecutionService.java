package com.archdox.cloud.aipolicy.application;

import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.domain.AiProviderCredentialStatus;
import com.archdox.cloud.aipolicy.domain.OfficeAiPolicy;
import com.archdox.cloud.aipolicy.infra.AiProviderCredentialRepository;
import com.archdox.cloud.aipolicy.infra.OfficeAiPolicyRepository;
import com.archdox.cloud.global.api.BadRequestException;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiPolicyExecutionService {
    private final OfficeAiPolicyRepository officePolicyRepository;
    private final AiProviderCredentialRepository providerRepository;
    private final AiBudgetGuardService budgetGuardService;

    public AiPolicyExecutionService(
            OfficeAiPolicyRepository officePolicyRepository,
            AiProviderCredentialRepository providerRepository,
            AiBudgetGuardService budgetGuardService
    ) {
        this.officePolicyRepository = officePolicyRepository;
        this.providerRepository = providerRepository;
        this.budgetGuardService = budgetGuardService;
    }

    @Transactional(readOnly = true)
    public AiExecutionPlan requireAllowed(Long officeId, AiFeature feature) {
        return requireAllowed(officeId, null, feature);
    }

    @Transactional(readOnly = true)
    public AiExecutionPlan requireAllowed(Long officeId, Long userId, AiFeature feature) {
        var policy = officePolicyRepository.findByOfficeId(officeId)
                .orElseThrow(() -> disabled("AI policy is not configured for this office"));
        if (!policy.aiEnabled()) {
            throw disabled("AI is disabled for this office");
        }
        if (!featureAllowed(policy, feature)) {
            throw disabled("AI feature is not enabled for this office: " + feature.name());
        }
        if (policy.preferredProviderCredentialId() == null) {
            throw disabled("AI provider credential is not assigned for this office");
        }
        var provider = providerRepository.findById(policy.preferredProviderCredentialId())
                .orElseThrow(() -> disabled("Assigned AI provider credential was not found"));
        if (provider.status() != AiProviderCredentialStatus.ACTIVE) {
            throw disabled("Assigned AI provider credential is not active");
        }
        var model = provider.defaultModel();
        if (model == null || model.isBlank()) {
            throw disabled("Assigned AI provider default model is not configured");
        }
        budgetGuardService.requireWithinBudget(policy, userId, provider.providerCode(), model.trim(), OffsetDateTime.now());
        return new AiExecutionPlan(
                officeId,
                userId,
                feature,
                provider,
                new ModelId(provider.providerCode(), model.trim()),
                policy.maxOutputTokens());
    }

    @Transactional(readOnly = true)
    public Optional<AiExecutionPlan> findAllowed(Long officeId, AiFeature feature) {
        return findAllowed(officeId, null, feature);
    }

    @Transactional(readOnly = true)
    public Optional<AiExecutionPlan> findAllowed(Long officeId, Long userId, AiFeature feature) {
        try {
            return Optional.of(requireAllowed(officeId, userId, feature));
        } catch (BadRequestException ex) {
            return Optional.empty();
        }
    }

    private boolean featureAllowed(OfficeAiPolicy policy, AiFeature feature) {
        return switch (feature) {
            case DOCUMENT_REVIEW -> policy.documentReviewAiEnabled();
            case DOCUMENT_GENERATION -> policy.documentGenerationAiEnabled();
        };
    }

    private BadRequestException disabled(String message) {
        return new BadRequestException(
                "AI_FEATURE_NOT_AVAILABLE",
                "error.ai.featureNotAvailable",
                message);
    }
}
