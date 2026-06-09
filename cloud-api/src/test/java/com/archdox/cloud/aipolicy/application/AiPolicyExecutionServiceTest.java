package com.archdox.cloud.aipolicy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.aipolicy.domain.AiCredentialDeliveryMode;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.domain.AiProviderType;
import com.archdox.cloud.aipolicy.domain.OfficeAiPolicy;
import com.archdox.cloud.aipolicy.infra.AiProviderCredentialRepository;
import com.archdox.cloud.aipolicy.infra.OfficeAiPolicyRepository;
import com.archdox.cloud.global.api.BadRequestException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiPolicyExecutionServiceTest {
    @Test
    void resolvesActiveOfficePolicyToHarnessModelId() {
        var now = OffsetDateTime.now();
        var officePolicy = new OfficeAiPolicy(10L, 1L, now);
        officePolicy.update(true, true, false, 42L, AiCredentialDeliveryMode.PROXY_ONLY, null, null, null, null, null, 1L, now);
        var provider = new AiProviderCredential(
                "openai-main",
                "OpenAI Main",
                AiProviderType.OPENAI,
                "https://api.openai.com/v1",
                "gpt-4.1-mini",
                "encrypted",
                "fingerprint",
                1L,
                now);
        provider.publish(now);

        var officePolicies = mock(OfficeAiPolicyRepository.class);
        var providers = mock(AiProviderCredentialRepository.class);
        var budgetGuard = mock(AiBudgetGuardService.class);
        when(officePolicies.findByOfficeId(10L)).thenReturn(Optional.of(officePolicy));
        when(providers.findById(42L)).thenReturn(Optional.of(provider));

        var plan = new AiPolicyExecutionService(officePolicies, providers, budgetGuard)
                .requireAllowed(10L, 11L, AiFeature.DOCUMENT_REVIEW);

        assertThat(plan.officeId()).isEqualTo(10L);
        assertThat(plan.userId()).isEqualTo(11L);
        assertThat(plan.feature()).isEqualTo(AiFeature.DOCUMENT_REVIEW);
        assertThat(plan.provider()).isSameAs(provider);
        assertThat(plan.modelId().asString()).isEqualTo("openai-main:gpt-4.1-mini");
        assertThat(plan.maxOutputTokens()).isEqualTo(2000);
        verify(budgetGuard).requireWithinBudget(
                org.mockito.ArgumentMatchers.same(officePolicy),
                org.mockito.ArgumentMatchers.eq(11L),
                org.mockito.ArgumentMatchers.eq("openai-main"),
                org.mockito.ArgumentMatchers.eq("gpt-4.1-mini"),
                org.mockito.ArgumentMatchers.any(OffsetDateTime.class));
    }

    @Test
    void rejectsFeatureDisabledByOfficePolicy() {
        var now = OffsetDateTime.now();
        var officePolicy = new OfficeAiPolicy(10L, 1L, now);
        officePolicy.update(true, true, false, 42L, AiCredentialDeliveryMode.PROXY_ONLY, null, null, null, null, null, 1L, now);

        var officePolicies = mock(OfficeAiPolicyRepository.class);
        var providers = mock(AiProviderCredentialRepository.class);
        var budgetGuard = mock(AiBudgetGuardService.class);
        when(officePolicies.findByOfficeId(10L)).thenReturn(Optional.of(officePolicy));

        assertThatThrownBy(() -> new AiPolicyExecutionService(officePolicies, providers, budgetGuard)
                .requireAllowed(10L, AiFeature.DOCUMENT_GENERATION))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("AI feature is not enabled");
    }
}
