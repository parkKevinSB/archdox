package com.archdox.cloud.aipolicy.application;

import com.archdox.cloud.aipolicy.domain.AiCredentialDeliveryMode;
import com.archdox.cloud.aipolicy.domain.AiPolicyDefaults;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.domain.AiProviderCredentialStatus;
import com.archdox.cloud.aipolicy.domain.AiProviderType;
import com.archdox.cloud.aipolicy.domain.OfficeAiPolicy;
import com.archdox.cloud.aipolicy.infra.AiProviderCredentialRepository;
import com.archdox.cloud.aipolicy.infra.OfficeAiPolicyRepository;
import com.archdox.cloud.office.infra.OfficeRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AiDevBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AiDevBootstrap.class);

    private final AiDevBootstrapProperties properties;
    private final AiProviderCredentialRepository providerRepository;
    private final OfficeAiPolicyRepository officePolicyRepository;
    private final OfficeRepository officeRepository;

    public AiDevBootstrap(
            AiDevBootstrapProperties properties,
            AiProviderCredentialRepository providerRepository,
            OfficeAiPolicyRepository officePolicyRepository,
            OfficeRepository officeRepository
    ) {
        this.properties = properties;
        this.providerRepository = providerRepository;
        this.officePolicyRepository = officePolicyRepository;
        this.officeRepository = officeRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        var now = OffsetDateTime.now();
        var reviewProvider = ensureProvider(
                properties.providerCode(),
                properties.providerDisplayName(),
                properties.defaultModel(),
                now);
        ensureProvider(
                properties.opsProviderCode(),
                properties.opsProviderDisplayName(),
                properties.opsDefaultModel(),
                now);
        if (properties.isAttachToExistingOffices()) {
            attachReviewProviderToOffices(reviewProvider, now);
        }
    }

    private AiProviderCredential ensureProvider(
            String providerCode,
            String displayName,
            String defaultModel,
            OffsetDateTime now
    ) {
        var normalizedCode = providerCode.trim().toLowerCase(Locale.ROOT);
        var provider = providerRepository.findByProviderCode(normalizedCode)
                .orElseGet(() -> providerRepository.saveAndFlush(new AiProviderCredential(
                        normalizedCode,
                        displayName,
                        AiProviderType.CUSTOM_HTTP,
                        null,
                        defaultModel,
                        null,
                        null,
                        null,
                        now)));
        provider.update(displayName, AiProviderType.CUSTOM_HTTP, null, defaultModel, false, null, null, now);
        if (provider.status() != AiProviderCredentialStatus.ACTIVE) {
            provider.publish(now);
            log.info("Bootstrapped development AI provider: {}", normalizedCode);
        }
        return provider;
    }

    private void attachReviewProviderToOffices(AiProviderCredential provider, OffsetDateTime now) {
        var offices = officeRepository.findAll();
        var changedOfficeIds = new LinkedHashSet<Long>();
        var skippedOfficeIds = new LinkedHashSet<Long>();
        for (var office : offices) {
            var policy = officePolicyRepository.findByOfficeId(office.id())
                    .orElseGet(() -> officePolicyRepository.save(new OfficeAiPolicy(office.id(), null, now)));
            if (policy.preferredProviderCredentialId() != null) {
                skippedOfficeIds.add(office.id());
                continue;
            }
            policy.update(
                    true,
                    properties.isDocumentReviewAiEnabled(),
                    properties.isDocumentGenerationAiEnabled(),
                    provider.id(),
                    AiCredentialDeliveryMode.PROXY_ONLY,
                    true,
                    null,
                    "USD",
                    AiPolicyDefaults.OFFICE_DAILY_CALL_LIMIT,
                    AiPolicyDefaults.OFFICE_MONTHLY_TOKEN_LIMIT,
                    AiPolicyDefaults.OFFICE_MAX_OUTPUT_TOKENS,
                    AiPolicyDefaults.USER_DAILY_CALL_LIMIT,
                    AiPolicyDefaults.USER_MONTHLY_TOKEN_LIMIT,
                    null,
                    now);
            changedOfficeIds.add(office.id());
        }
        if (!changedOfficeIds.isEmpty()) {
            log.info("Bootstrapped development AI policy for offices={}", changedOfficeIds);
        }
        if (!skippedOfficeIds.isEmpty()) {
            log.info("Skipped development AI policy bootstrap for already configured offices={}", skippedOfficeIds);
        }
    }
}
