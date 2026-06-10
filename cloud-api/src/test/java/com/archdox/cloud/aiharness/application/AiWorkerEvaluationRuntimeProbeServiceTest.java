package com.archdox.cloud.aiharness.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.aipolicy.application.AiHarnessExecutionPlan;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyResolution;
import com.archdox.cloud.aipolicy.application.AiProviderConnectionTestService;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.domain.AiProviderType;
import com.archdox.cloud.aipolicy.dto.AiProviderConnectionTestResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AiWorkerEvaluationRuntimeProbeServiceTest {
    private final PlatformAdminService platformAdminService = mock(PlatformAdminService.class);
    private final AiWorkerEvaluationReadService readService = new AiWorkerEvaluationReadService(platformAdminService);
    private final AiHarnessPolicyExecutionService policyExecutionService = mock(AiHarnessPolicyExecutionService.class);
    private final AiProviderConnectionTestService connectionTestService = mock(AiProviderConnectionTestService.class);
    private final AiWorkerEvaluationTokenControlService tokenControlService = mock(AiWorkerEvaluationTokenControlService.class);
    private final AiWorkerEvaluationRuntimeProbeService service = new AiWorkerEvaluationRuntimeProbeService(
            readService,
            policyExecutionService,
            connectionTestService,
            tokenControlService);

    @Test
    void runtimeProbeTestsUniqueRealProviderAndMarksRealModelSignalPass() {
        var principal = new UserPrincipal(7L, "platform@test.co.kr");
        var provider = provider(4L, "openai-main", "OpenAI Main", AiProviderType.OPENAI, "gpt-4.1-mini");
        var plan = new AiHarnessExecutionPlan(
                AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT,
                provider,
                new ModelId("openai-main", "gpt-4.1-mini"),
                3,
                Duration.ofSeconds(120));
        when(policyExecutionService.resolve(AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT))
                .thenReturn(AiHarnessPolicyResolution.runnable(plan));
        when(policyExecutionService.resolve(AiHarnessPolicyKey.PLATFORM_OPS_DIAGNOSIS))
                .thenReturn(AiHarnessPolicyResolution.runnable(new AiHarnessExecutionPlan(
                        AiHarnessPolicyKey.PLATFORM_OPS_DIAGNOSIS,
                        provider,
                        new ModelId("openai-main", "gpt-4.1-mini"),
                        3,
                        Duration.ofSeconds(120))));
        when(policyExecutionService.resolve(AiHarnessPolicyKey.DOCUMENT_NARRATIVE_POLISH))
                .thenReturn(AiHarnessPolicyResolution.runnable(new AiHarnessExecutionPlan(
                        AiHarnessPolicyKey.DOCUMENT_NARRATIVE_POLISH,
                        provider,
                        new ModelId("openai-main", "gpt-4.1-mini"),
                        2,
                        Duration.ofSeconds(90))));
        when(policyExecutionService.resolve(AiHarnessPolicyKey.SOURCE_BACKED_LEGAL_REVIEW))
                .thenReturn(AiHarnessPolicyResolution.runnable(new AiHarnessExecutionPlan(
                        AiHarnessPolicyKey.SOURCE_BACKED_LEGAL_REVIEW,
                        provider,
                        new ModelId("openai-main", "gpt-4.1-mini"),
                        2,
                        Duration.ofSeconds(90))));
        when(connectionTestService.testProvider(principal, 4L))
                .thenReturn(new AiProviderConnectionTestResponse(
                        4L,
                        "openai-main",
                        "OPENAI",
                        "gpt-4.1-mini",
                        true,
                        "SUCCEEDED",
                        "AI provider connection test succeeded.",
                        123L,
                        "stop",
                        "{\"status\":\"ok\"}",
                        OffsetDateTime.parse("2026-06-09T00:00:00+09:00")));
        when(tokenControlService.tokenControlGroups()).thenReturn(List.of());
        when(tokenControlService.tokenControlSignals(List.of())).thenReturn(List.of());

        var summary = service.runtimeProbe(principal);

        assertThat(summary.evaluationMode()).isEqualTo("RUNTIME_PROVIDER_PROBE");
        assertThat(summary.totalCases()).isEqualTo(52);
        assertThat(summary.passedCases()).isEqualTo(52);
        assertThat(summary.warningCases()).isZero();
        assertThat(summary.failedCases()).isZero();
        assertThat(summary.groups()).hasSize(6);
        assertThat(summary.groups().getLast().groupKey()).isEqualTo("RUNTIME_AI_PROVIDER_PROBE");
        assertThat(summary.signals()).anySatisfy(signal -> {
            assertThat(signal.signalKey()).isEqualTo("REAL_MODEL_EVALUATION");
            assertThat(signal.status()).isEqualTo("PASS");
        });
        assertThat(summary.signals()).anySatisfy(signal -> {
            assertThat(signal.signalKey()).isEqualTo("RUNTIME_PROVIDER_CONNECTIVITY");
            assertThat(signal.status()).isEqualTo("PASS");
        });
        verify(connectionTestService).testProvider(principal, 4L);
    }

    @Test
    void runtimeProbeWarnsAndSkipsExternalCallForFakeProvider() {
        var principal = new UserPrincipal(7L, "platform@test.co.kr");
        var provider = provider(2L, "fake-review", "Development Fake AI", AiProviderType.CUSTOM_HTTP, "fake-review-model");
        when(policyExecutionService.resolve(AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT))
                .thenReturn(AiHarnessPolicyResolution.runnable(new AiHarnessExecutionPlan(
                        AiHarnessPolicyKey.LEGAL_DIGEST_ENRICHMENT,
                        provider,
                        new ModelId("fake-review", "fake-review-model"),
                        3,
                        Duration.ofSeconds(30))));
        when(policyExecutionService.resolve(AiHarnessPolicyKey.PLATFORM_OPS_DIAGNOSIS))
                .thenReturn(AiHarnessPolicyResolution.unavailable(
                        AiHarnessPolicyKey.PLATFORM_OPS_DIAGNOSIS,
                        "PROVIDER_NOT_ASSIGNED"));
        when(policyExecutionService.resolve(AiHarnessPolicyKey.DOCUMENT_NARRATIVE_POLISH))
                .thenReturn(AiHarnessPolicyResolution.runnable(new AiHarnessExecutionPlan(
                        AiHarnessPolicyKey.DOCUMENT_NARRATIVE_POLISH,
                        provider,
                        new ModelId("fake-review", "fake-review-model"),
                        2,
                        Duration.ofSeconds(90))));
        when(policyExecutionService.resolve(AiHarnessPolicyKey.SOURCE_BACKED_LEGAL_REVIEW))
                .thenReturn(AiHarnessPolicyResolution.runnable(new AiHarnessExecutionPlan(
                        AiHarnessPolicyKey.SOURCE_BACKED_LEGAL_REVIEW,
                        provider,
                        new ModelId("fake-review", "fake-review-model"),
                        2,
                        Duration.ofSeconds(90))));
        when(tokenControlService.tokenControlGroups()).thenReturn(List.of());
        when(tokenControlService.tokenControlSignals(List.of())).thenReturn(List.of());

        var summary = service.runtimeProbe(principal);

        assertThat(summary.evaluationMode()).isEqualTo("RUNTIME_PROVIDER_PROBE");
        assertThat(summary.warningCases()).isEqualTo(2);
        assertThat(summary.signals()).anySatisfy(signal -> {
            assertThat(signal.signalKey()).isEqualTo("REAL_MODEL_EVALUATION");
            assertThat(signal.status()).isEqualTo("WARN");
        });
        verify(connectionTestService, never()).testProvider(principal, 2L);
    }

    private AiProviderCredential provider(
            Long id,
            String providerCode,
            String displayName,
            AiProviderType providerType,
            String defaultModel
    ) {
        var now = OffsetDateTime.parse("2026-06-09T00:00:00+09:00");
        var provider = new AiProviderCredential(
                providerCode,
                displayName,
                providerType,
                null,
                defaultModel,
                "encrypted",
                "fingerprint",
                1L,
                now);
        ReflectionTestUtils.setField(provider, "id", id);
        provider.publish(now);
        return provider;
    }
}
