package com.archdox.cloud.aipolicy.application;

import com.archdox.cloud.aipolicy.domain.AiHarnessPolicy;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.domain.AiProviderCredentialStatus;
import com.archdox.cloud.aipolicy.infra.AiHarnessPolicyRepository;
import com.archdox.cloud.aipolicy.infra.AiProviderCredentialRepository;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiHarnessPolicyExecutionService {
    private final AiHarnessPolicyRepository policyRepository;
    private final AiProviderCredentialRepository providerRepository;

    public AiHarnessPolicyExecutionService(
            AiHarnessPolicyRepository policyRepository,
            AiProviderCredentialRepository providerRepository
    ) {
        this.policyRepository = policyRepository;
        this.providerRepository = providerRepository;
    }

    @Transactional(readOnly = true)
    public AiHarnessPolicyResolution resolve(AiHarnessPolicyKey policyKey) {
        var policy = policyRepository.findByPolicyKey(policyKey).orElse(null);
        if (policy == null) {
            return AiHarnessPolicyResolution.unavailable(policyKey, "POLICY_NOT_CONFIGURED");
        }
        if (!policy.enabled()) {
            return AiHarnessPolicyResolution.unavailable(policyKey, "POLICY_DISABLED");
        }
        if (policy.providerCredentialId() == null) {
            return AiHarnessPolicyResolution.unavailable(policyKey, "PROVIDER_NOT_ASSIGNED");
        }
        var provider = providerRepository.findById(policy.providerCredentialId()).orElse(null);
        if (provider == null) {
            return AiHarnessPolicyResolution.unavailable(policyKey, "PROVIDER_NOT_FOUND");
        }
        if (provider.status() != AiProviderCredentialStatus.ACTIVE) {
            return AiHarnessPolicyResolution.unavailable(policyKey, "PROVIDER_NOT_ACTIVE");
        }
        var modelName = modelName(policy, provider);
        if (modelName == null) {
            return AiHarnessPolicyResolution.unavailable(policyKey, "MODEL_NOT_CONFIGURED");
        }
        return AiHarnessPolicyResolution.runnable(new AiHarnessExecutionPlan(
                policyKey,
                provider,
                new ModelId(provider.providerCode(), modelName),
                policy.maxAttempts(),
                Duration.ofSeconds(policy.timeoutSeconds())));
    }

    private String modelName(AiHarnessPolicy policy, AiProviderCredential provider) {
        var policyModel = blankToNull(policy.modelName());
        if (policyModel != null) {
            return policyModel;
        }
        return blankToNull(provider.defaultModel());
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
