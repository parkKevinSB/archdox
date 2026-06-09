package com.archdox.cloud.aiharness.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.archdox.cloud.aipolicy.application.AiHarnessExecutionPlan;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyResolution;
import com.archdox.cloud.aipolicy.domain.AiCredentialDeliveryMode;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.aipolicy.domain.AiModelCallLog;
import com.archdox.cloud.aipolicy.domain.AiModelCallLogStatus;
import com.archdox.cloud.aipolicy.domain.AiModelPricingRuleStatus;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.domain.AiProviderType;
import com.archdox.cloud.aipolicy.domain.OfficeAiPolicy;
import com.archdox.cloud.aipolicy.infra.AiModelCallLogRepository;
import com.archdox.cloud.aipolicy.infra.AiModelPricingRuleRepository;
import com.archdox.cloud.aipolicy.infra.AiUsageGroupProjection;
import com.archdox.cloud.aipolicy.infra.OfficeAiPolicyRepository;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class AiWorkerEvaluationTokenControlServiceTest {
    private final AiHarnessPolicyExecutionService policyExecutionService = mock(AiHarnessPolicyExecutionService.class);
    private final AiModelCallLogRepository callLogRepository = mock(AiModelCallLogRepository.class);
    private final AiModelPricingRuleRepository pricingRuleRepository = mock(AiModelPricingRuleRepository.class);
    private final OfficeAiPolicyRepository officePolicyRepository = mock(OfficeAiPolicyRepository.class);
    private final AiWorkerEvaluationTokenControlService service = new AiWorkerEvaluationTokenControlService(
            policyExecutionService,
            callLogRepository,
            pricingRuleRepository,
            officePolicyRepository);

    @Test
    void tokenControlPassesWhenHarnessesAreBoundedAndUsageIsObservable() {
        var provider = provider(4L, "openai-main", AiProviderType.OPENAI, "gpt-4.1-mini");
        when(policyExecutionService.resolve(AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT))
                .thenReturn(runnable(AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT, provider, 3, 120));
        when(policyExecutionService.resolve(AiHarnessPolicyKey.PLATFORM_OPS_DIAGNOSIS))
                .thenReturn(runnable(AiHarnessPolicyKey.PLATFORM_OPS_DIAGNOSIS, provider, 2, 90));
        when(pricingRuleRepository.existsByProviderCodeAndModelNameAndStatus(
                eq("openai-main"),
                eq("gpt-4.1-mini"),
                eq(AiModelPricingRuleStatus.ACTIVE))).thenReturn(true);
        when(officePolicyRepository.findAll()).thenReturn(List.of(officePolicy(true)));
        when(callLogRepository.findAllByOrderByCompletedAtDesc(any(Pageable.class)))
                .thenReturn(List.of(callLog("LEGAL_DIGEST_ENRICHMENT", 1_200, 300, OffsetDateTime.now())));
        when(callLogRepository.usageByOfficeAndFeature(any(), any(), any(), any()))
                .thenReturn(List.of(usageGroup(3L, "LEGAL_DIGEST_ENRICHMENT", 1L, 1_200L, 300L)));

        var groups = service.tokenControlGroups();
        var signals = service.tokenControlSignals(groups);

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().groupKey()).isEqualTo("TOKEN_COST_CONTROL");
        assertThat(groups.getFirst().failedCases()).isZero();
        assertThat(groups.getFirst().warningCases()).isZero();
        assertThat(groups.getFirst().passedCases()).isEqualTo(5);
        assertThat(signals).singleElement().satisfies(signal -> assertThat(signal.status()).isEqualTo("PASS"));
    }

    @Test
    void tokenControlFailsWhenHarnessCanRepeatTooMuchAndRecentUsageBursts() {
        var provider = provider(4L, "openai-main", AiProviderType.OPENAI, "gpt-4.1-mini");
        when(policyExecutionService.resolve(AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT))
                .thenReturn(runnable(AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT, provider, 6, 700));
        when(policyExecutionService.resolve(AiHarnessPolicyKey.PLATFORM_OPS_DIAGNOSIS))
                .thenReturn(AiHarnessPolicyResolution.unavailable(
                        AiHarnessPolicyKey.PLATFORM_OPS_DIAGNOSIS,
                        "PROVIDER_NOT_ASSIGNED"));
        when(officePolicyRepository.findAll()).thenReturn(List.of(officePolicy(false)));
        when(callLogRepository.findAllByOrderByCompletedAtDesc(any(Pageable.class))).thenReturn(burstLogs());
        when(callLogRepository.usageByOfficeAndFeature(any(), any(), any(), any()))
                .thenReturn(List.of(usageGroup(3L, "LEGAL_DIGEST_ENRICHMENT", 120L, 800_000L, 300_000L)));

        var groups = service.tokenControlGroups();
        var signals = service.tokenControlSignals(groups);

        assertThat(groups.getFirst().failedCases()).isGreaterThanOrEqualTo(1);
        assertThat(groups.getFirst().warningCases()).isGreaterThanOrEqualTo(1);
        assertThat(signals).singleElement().satisfies(signal -> assertThat(signal.status()).isEqualTo("FAILED"));
    }

    private AiHarnessPolicyResolution runnable(
            AiHarnessPolicyKey key,
            AiProviderCredential provider,
            int maxAttempts,
            long timeoutSeconds
    ) {
        return AiHarnessPolicyResolution.runnable(new AiHarnessExecutionPlan(
                key,
                provider,
                new ModelId(provider.providerCode(), provider.defaultModel()),
                maxAttempts,
                Duration.ofSeconds(timeoutSeconds)));
    }

    private AiProviderCredential provider(Long id, String providerCode, AiProviderType type, String modelName) {
        var now = OffsetDateTime.now();
        var provider = new AiProviderCredential(
                providerCode,
                providerCode,
                type,
                null,
                modelName,
                "encrypted",
                "fingerprint",
                1L,
                now);
        ReflectionTestUtils.setField(provider, "id", id);
        provider.publish(now);
        return provider;
    }

    private OfficeAiPolicy officePolicy(boolean budgetEnabled) {
        var now = OffsetDateTime.now();
        var policy = new OfficeAiPolicy(3L, 1L, now);
        policy.update(
                true,
                true,
                true,
                4L,
                AiCredentialDeliveryMode.PROXY_ONLY,
                budgetEnabled,
                budgetEnabled ? new BigDecimal("20.00") : null,
                "USD",
                budgetEnabled ? 100 : null,
                budgetEnabled ? 1_000_000L : null,
                1L,
                now);
        return policy;
    }

    private AiModelCallLog callLog(String feature, Integer inputTokens, Integer outputTokens, OffsetDateTime completedAt) {
        return new AiModelCallLog(
                "call-" + completedAt.toInstant().toEpochMilli(),
                3L,
                4L,
                "openai-main",
                "OPENAI",
                "openai-main:gpt-4.1-mini",
                "gpt-4.1-mini",
                feature,
                "legal-digest-ai-draft",
                "change-set:8",
                "LEGAL_CHANGE_SET",
                "8",
                AiModelCallLogStatus.SUCCEEDED,
                inputTokens,
                outputTokens,
                1_200L,
                "stop",
                "resp-1",
                null,
                null,
                10L,
                "USD",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                completedAt.minusSeconds(2),
                completedAt);
    }

    private List<AiModelCallLog> burstLogs() {
        var now = OffsetDateTime.now();
        var logs = new ArrayList<AiModelCallLog>();
        for (var i = 0; i < 101; i++) {
            logs.add(callLog("LEGAL_DIGEST_ENRICHMENT", 8_000, 3_000, now.minusMinutes(i % 30)));
        }
        return logs;
    }

    private AiUsageGroupProjection usageGroup(
            Long officeId,
            String feature,
            Long callCount,
            Long inputTokens,
            Long outputTokens
    ) {
        return new AiUsageGroupProjection() {
            @Override
            public Long getOfficeId() {
                return officeId;
            }

            @Override
            public String getFeature() {
                return feature;
            }

            @Override
            public Long getCallCount() {
                return callCount;
            }

            @Override
            public Long getSucceededCount() {
                return callCount;
            }

            @Override
            public Long getFailedCount() {
                return 0L;
            }

            @Override
            public Long getInputTokens() {
                return inputTokens;
            }

            @Override
            public Long getOutputTokens() {
                return outputTokens;
            }

            @Override
            public BigDecimal getEstimatedTotalCost() {
                return BigDecimal.ZERO;
            }
        };
    }
}
