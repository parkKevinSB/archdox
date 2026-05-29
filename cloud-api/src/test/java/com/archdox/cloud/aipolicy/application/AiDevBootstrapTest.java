package com.archdox.cloud.aipolicy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.archdox.cloud.aipolicy.domain.AiCredentialDeliveryMode;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.domain.AiProviderCredentialStatus;
import com.archdox.cloud.aipolicy.domain.OfficeAiPolicy;
import com.archdox.cloud.aipolicy.infra.AiProviderCredentialRepository;
import com.archdox.cloud.aipolicy.infra.OfficeAiPolicyRepository;
import com.archdox.cloud.office.domain.Office;
import com.archdox.cloud.office.infra.OfficeRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

class AiDevBootstrapTest {

    @Test
    void disabledBootstrapDoesNothing() {
        var properties = new AiDevBootstrapProperties();
        var providerRepository = mock(AiProviderCredentialRepository.class);
        var officePolicyRepository = mock(OfficeAiPolicyRepository.class);
        var officeRepository = mock(OfficeRepository.class);
        var bootstrap = new AiDevBootstrap(
                properties,
                providerRepository,
                officePolicyRepository,
                officeRepository);

        bootstrap.run(mock(ApplicationArguments.class));

        verifyNoInteractions(providerRepository, officePolicyRepository, officeRepository);
    }

    @Test
    void enabledBootstrapCreatesFakeProvidersAndOfficeReviewPolicy() {
        var properties = new AiDevBootstrapProperties();
        properties.setEnabled(true);
        var providerRepository = mock(AiProviderCredentialRepository.class);
        var officePolicyRepository = mock(OfficeAiPolicyRepository.class);
        var officeRepository = mock(OfficeRepository.class);
        var office = mock(Office.class);
        var ids = new AtomicLong(100L);
        when(office.id()).thenReturn(10L);
        when(providerRepository.findByProviderCode("fake-review")).thenReturn(Optional.empty());
        when(providerRepository.findByProviderCode("fake-ops")).thenReturn(Optional.empty());
        when(providerRepository.saveAndFlush(any(AiProviderCredential.class))).thenAnswer(invocation -> {
            var provider = invocation.getArgument(0, AiProviderCredential.class);
            ReflectionTestUtils.setField(provider, "id", ids.getAndIncrement());
            return provider;
        });
        when(officeRepository.findAll()).thenReturn(List.of(office));
        when(officePolicyRepository.findByOfficeId(10L)).thenReturn(Optional.empty());
        when(officePolicyRepository.save(any(OfficeAiPolicy.class))).thenAnswer(invocation -> {
            var policy = invocation.getArgument(0, OfficeAiPolicy.class);
            ReflectionTestUtils.setField(policy, "id", 200L);
            return policy;
        });
        var bootstrap = new AiDevBootstrap(
                properties,
                providerRepository,
                officePolicyRepository,
                officeRepository);

        bootstrap.run(mock(ApplicationArguments.class));

        var providerCaptor = ArgumentCaptor.forClass(AiProviderCredential.class);
        verify(providerRepository, times(2)).saveAndFlush(providerCaptor.capture());
        assertThat(providerCaptor.getAllValues())
                .extracting(AiProviderCredential::providerCode)
                .containsExactly("fake-review", "fake-ops");
        assertThat(providerCaptor.getAllValues())
                .extracting(AiProviderCredential::status)
                .containsExactly(AiProviderCredentialStatus.ACTIVE, AiProviderCredentialStatus.ACTIVE);
        assertThat(providerCaptor.getAllValues())
                .extracting(AiProviderCredential::defaultModel)
                .containsExactly("fake-review-model", "fake-ops-model");

        var policyCaptor = ArgumentCaptor.forClass(OfficeAiPolicy.class);
        verify(officePolicyRepository).save(policyCaptor.capture());
        var policy = policyCaptor.getValue();
        assertThat(policy.officeId()).isEqualTo(10L);
        assertThat(policy.aiEnabled()).isTrue();
        assertThat(policy.documentReviewAiEnabled()).isTrue();
        assertThat(policy.documentGenerationAiEnabled()).isFalse();
        assertThat(policy.preferredProviderCredentialId()).isEqualTo(100L);
        assertThat(policy.credentialDeliveryMode()).isEqualTo(AiCredentialDeliveryMode.PROXY_ONLY);
        assertThat(policy.budgetEnforcementEnabled()).isFalse();
    }
}
