package com.archdox.cloud.aipolicy.application;

import com.archdox.cloud.aipolicy.domain.AiCredentialDeliveryMode;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.domain.AiProviderCredentialStatus;
import com.archdox.cloud.aipolicy.domain.OfficeAiPolicy;
import java.util.LinkedHashMap;
import java.util.Map;

public record EffectiveAgentAiPolicy(
        boolean enabled,
        boolean documentReviewEnabled,
        boolean documentGenerationEnabled,
        AiCredentialDeliveryMode credentialDeliveryMode,
        long policyVersion,
        Long providerCredentialId,
        String providerCode,
        String providerType,
        String baseUrl,
        String defaultModel,
        Long credentialVersion,
        boolean apiKeyDelivered,
        String message
) {
    public static EffectiveAgentAiPolicy disabled(String message) {
        return new EffectiveAgentAiPolicy(
                false,
                false,
                false,
                AiCredentialDeliveryMode.PROXY_ONLY,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                message);
    }

    public static EffectiveAgentAiPolicy from(OfficeAiPolicy policy, AiProviderCredential provider, String message) {
        var providerActive = provider != null && provider.status() == AiProviderCredentialStatus.ACTIVE;
        var enabled = policy.aiEnabled() && providerActive;
        return new EffectiveAgentAiPolicy(
                enabled,
                enabled && policy.documentReviewAiEnabled(),
                enabled && policy.documentGenerationAiEnabled(),
                policy.credentialDeliveryMode(),
                policy.policyVersion(),
                provider == null ? null : provider.id(),
                provider == null ? null : provider.providerCode(),
                provider == null ? null : provider.providerType().name(),
                provider == null ? null : provider.baseUrl(),
                provider == null ? null : provider.defaultModel(),
                provider == null ? null : provider.credentialVersion(),
                false,
                message);
    }

    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("enabled", enabled);
        map.put("documentReviewEnabled", documentReviewEnabled);
        map.put("documentGenerationEnabled", documentGenerationEnabled);
        map.put("credentialDeliveryMode", credentialDeliveryMode.name());
        map.put("policyVersion", policyVersion);
        map.put("apiKeyDelivered", apiKeyDelivered);
        map.put("message", message);
        if (providerCredentialId != null) {
            map.put("provider", Map.of(
                    "credentialId", providerCredentialId,
                    "providerCode", providerCode,
                    "providerType", providerType,
                    "baseUrl", baseUrl == null ? "" : baseUrl,
                    "defaultModel", defaultModel == null ? "" : defaultModel,
                    "credentialVersion", credentialVersion == null ? 0L : credentialVersion));
        }
        return map;
    }
}
