package com.archdox.cloud.aipolicy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.account.domain.UserAccount;
import com.archdox.cloud.account.infra.UserAccountRepository;
import com.archdox.cloud.aipolicy.domain.AiCredentialDeliveryMode;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicy;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.aipolicy.domain.AiModelPricingRule;
import com.archdox.cloud.aipolicy.domain.AiModelPricingRuleStatus;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.domain.AiProviderType;
import com.archdox.cloud.aipolicy.domain.OfficeAiPolicy;
import com.archdox.cloud.aipolicy.infra.AiHarnessPolicyRepository;
import com.archdox.cloud.aipolicy.infra.AiModelCallLogRepository;
import com.archdox.cloud.aipolicy.infra.AiModelPricingRuleRepository;
import com.archdox.cloud.aipolicy.infra.AiProviderCredentialRepository;
import com.archdox.cloud.aipolicy.infra.AiUserBudgetOverrideRepository;
import com.archdox.cloud.aipolicy.infra.AiUserUsageGroupProjection;
import com.archdox.cloud.aipolicy.infra.OfficeAiPolicyRepository;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.office.domain.Office;
import com.archdox.cloud.office.infra.OfficeRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.shared.OfficeType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AiBudgetUsageReadServiceTest {
    private final AiModelCallLogRepository callLogRepository = mock(AiModelCallLogRepository.class);
    private final OfficeAiPolicyRepository officePolicyRepository = mock(OfficeAiPolicyRepository.class);
    private final AiHarnessPolicyRepository harnessPolicyRepository = mock(AiHarnessPolicyRepository.class);
    private final AiProviderCredentialRepository providerRepository = mock(AiProviderCredentialRepository.class);
    private final AiModelPricingRuleRepository pricingRuleRepository = mock(AiModelPricingRuleRepository.class);
    private final AiUserBudgetOverrideRepository userBudgetOverrideRepository = mock(AiUserBudgetOverrideRepository.class);
    private final OfficeRepository officeRepository = mock(OfficeRepository.class);
    private final UserAccountRepository userRepository = mock(UserAccountRepository.class);
    private final PlatformAdminService platformAdminService = mock(PlatformAdminService.class);
    private final AiBudgetUsageReadService service = new AiBudgetUsageReadService(
            callLogRepository,
            officePolicyRepository,
            harnessPolicyRepository,
            providerRepository,
            pricingRuleRepository,
            userBudgetOverrideRepository,
            officeRepository,
            userRepository,
            platformAdminService);

    @Test
    void monthlySummaryCombinesOfficeHarnessUserAndPricingCoverage() {
        var principal = new UserPrincipal(7L, "platform@test.co.kr");
        var now = OffsetDateTime.parse("2026-06-09T00:00:00+09:00");
        var office = new Office("personal-001", "Personal Office", OfficeType.PERSONAL, "PERSONAL", now);
        ReflectionTestUtils.setField(office, "id", 10L);
        var policy = new OfficeAiPolicy(10L, 1L, now);
        policy.update(
                true,
                true,
                true,
                null,
                AiCredentialDeliveryMode.PROXY_ONLY,
                true,
                new BigDecimal("10.00"),
                "USD",
                100,
                1_000L,
                2_000,
                5,
                500L,
                1L,
                now);
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
        var harnessPolicy = new AiHarnessPolicy(AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT, 1L, now);
        harnessPolicy.update(true, 4L, "gpt-4.1-mini", 2, 90L, 1L, now);
        var pricingRule = new AiModelPricingRule(
                "openai-main",
                "gpt-4.1-mini",
                "USD",
                new BigDecimal("0.40000000"),
                new BigDecimal("1.60000000"),
                1L,
                now);
        ReflectionTestUtils.setField(pricingRule, "id", 30L);
        var user = new UserAccount("user@test.co.kr", "hash", "User", now);
        ReflectionTestUtils.setField(user, "id", 20L);
        var userUsage = mock(AiUserUsageGroupProjection.class);
        when(userUsage.getOfficeId()).thenReturn(10L);
        when(userUsage.getUserId()).thenReturn(20L);
        when(userUsage.getInputTokens()).thenReturn(100L);
        when(userUsage.getOutputTokens()).thenReturn(50L);

        when(officeRepository.findAll()).thenReturn(List.of(office));
        when(officePolicyRepository.findAll()).thenReturn(List.of(policy));
        when(providerRepository.findAll()).thenReturn(List.of(provider));
        when(harnessPolicyRepository.findAllByOrderByPolicyKeyAsc()).thenReturn(List.of(harnessPolicy));
        when(userBudgetOverrideRepository.findActiveAt(any(), any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(user));
        when(callLogRepository.countByOfficeIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(eq(10L), any(), any()))
                .thenReturn(10L);
        when(callLogRepository.sumTokensByOfficeIdAndCompletedAtRange(eq(10L), any(), any()))
                .thenReturn(200L);
        when(callLogRepository.sumEstimatedCostByOfficeIdAndCurrencyAndCompletedAtRange(eq(10L), eq("USD"), any(), any()))
                .thenReturn(new BigDecimal("1.23000000"));
        when(callLogRepository.countByFeatureAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                eq(AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT.name()),
                any(),
                any())).thenReturn(1L);
        when(callLogRepository.sumTokensByFeatureAndCompletedAtRange(
                eq(AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT.name()),
                any(),
                any())).thenReturn(50L);
        when(callLogRepository.sumEstimatedCostByFeatureAndCurrencyAndCompletedAtRange(
                eq(AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT.name()),
                eq("USD"),
                any(),
                any())).thenReturn(new BigDecimal("0.20000000"));
        when(callLogRepository.usageByOfficeAndUser(any(), any(), any())).thenReturn(List.of(userUsage));
        when(callLogRepository.countByOfficeIdAndUserIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                eq(10L),
                eq(20L),
                any(),
                any())).thenReturn(1L);
        when(pricingRuleRepository.findFirstByProviderCodeAndModelNameAndStatusOrderByCreatedAtDesc(
                eq("openai-main"),
                eq("gpt-4.1-mini"),
                eq(AiModelPricingRuleStatus.ACTIVE))).thenReturn(Optional.of(pricingRule));
        when(pricingRuleRepository.findFirstByProviderCodeAndModelNameAndStatusOrderByCreatedAtDesc(
                anyString(),
                eq("*"),
                eq(AiModelPricingRuleStatus.ACTIVE))).thenReturn(Optional.empty());

        var response = service.monthlySummary(principal);

        assertThat(response.offices()).hasSize(1);
        assertThat(response.offices().get(0).status()).isEqualTo("OK");
        assertThat(response.harnesses())
                .anySatisfy(row -> {
                    assertThat(row.policyKey()).isEqualTo("LEGAL_DIGEST_ENRICHMENT");
                    assertThat(row.pricingRuleConfigured()).isTrue();
                });
        assertThat(response.users()).hasSize(1);
        assertThat(response.users().get(0).monthlyTokens()).isEqualTo(150L);
        assertThat(response.activeUserOverrideCount()).isZero();
        assertThat(response.pricingCoverage())
                .anySatisfy(row -> {
                    assertThat(row.providerCode()).isEqualTo("openai-main");
                    assertThat(row.modelName()).isEqualTo("gpt-4.1-mini");
                    assertThat(row.configured()).isTrue();
                });
        verify(platformAdminService).requirePlatformAdmin(principal);
    }
}
