package com.archdox.cloud.aipolicy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.archdox.cloud.aipolicy.domain.AiHarnessPolicy;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.domain.AiProviderType;
import com.archdox.cloud.aipolicy.infra.AiHarnessPolicyRepository;
import com.archdox.cloud.aipolicy.infra.AiProviderCredentialRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AiHarnessPolicyExecutionServiceTest {
    private final AiHarnessPolicyRepository policyRepository = mock(AiHarnessPolicyRepository.class);
    private final AiProviderCredentialRepository providerRepository = mock(AiProviderCredentialRepository.class);
    private final AiHarnessPolicyExecutionService service = new AiHarnessPolicyExecutionService(
            policyRepository,
            providerRepository);

    @Test
    void resolvesRunnableHarnessPolicyToModelPlan() {
        var now = OffsetDateTime.parse("2026-06-09T00:00:00+09:00");
        var provider = new AiProviderCredential(
                "openai-main",
                "OpenAI Main",
                AiProviderType.OPENAI,
                null,
                "gpt-4.1-mini",
                "encrypted",
                "fingerprint",
                1L,
                now);
        ReflectionTestUtils.setField(provider, "id", 4L);
        provider.publish(now);
        var policy = new AiHarnessPolicy(AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT, 1L, now);
        policy.update(true, 4L, "gpt-4.1-mini", 3, 120L, 1L, now);

        when(policyRepository.findByPolicyKey(AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT))
                .thenReturn(Optional.of(policy));
        when(providerRepository.findById(4L)).thenReturn(Optional.of(provider));

        var resolution = service.resolve(AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT);

        assertThat(resolution.runnable()).isTrue();
        assertThat(resolution.plan().modelId().asString()).isEqualTo("openai-main:gpt-4.1-mini");
        assertThat(resolution.plan().maxAttempts()).isEqualTo(3);
        assertThat(resolution.plan().timeout().getSeconds()).isEqualTo(120L);
    }
}
