package com.archdox.cloud.aipolicy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.aipolicy.domain.AiModelPricingRule;
import com.archdox.cloud.aipolicy.domain.AiModelPricingRuleStatus;
import com.archdox.cloud.aipolicy.dto.CreateAiModelPricingRuleRequest;
import com.archdox.cloud.aipolicy.infra.AiModelPricingRuleRepository;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AiModelPricingRuleServiceTest {
    private final AiModelPricingRuleRepository repository = mock(AiModelPricingRuleRepository.class);
    private final PlatformAdminService platformAdminService = mock(PlatformAdminService.class);
    private final OperationEventService operationEventService = mock(OperationEventService.class);
    private final AiModelPricingRuleService service = new AiModelPricingRuleService(
            repository,
            platformAdminService,
            operationEventService);

    @Test
    void createPricingRuleDisablesExistingActiveRuleForSameProviderAndModel() {
        var principal = new UserPrincipal(7L, "platform@test.co.kr");
        var existing = new AiModelPricingRule(
                "openai-main",
                "gpt-4.1-mini",
                "USD",
                new BigDecimal("0.15000000"),
                new BigDecimal("0.60000000"),
                1L,
                OffsetDateTime.parse("2026-06-01T00:00:00+09:00"));
        ReflectionTestUtils.setField(existing, "id", 10L);
        when(repository.findByProviderCodeAndModelNameAndStatusOrderByCreatedAtDesc(
                "openai-main",
                "gpt-4.1-mini",
                AiModelPricingRuleStatus.ACTIVE)).thenReturn(List.of(existing));
        when(repository.save(any(AiModelPricingRule.class))).thenAnswer(invocation -> {
            var rule = invocation.getArgument(0, AiModelPricingRule.class);
            ReflectionTestUtils.setField(rule, "id", 11L);
            return rule;
        });

        var response = service.createPricingRule(
                principal,
                new CreateAiModelPricingRuleRequest(
                        "OPENAI-MAIN",
                        "gpt-4.1-mini",
                        "usd",
                        new BigDecimal("0.40"),
                        new BigDecimal("1.60")));

        assertThat(existing.status()).isEqualTo(AiModelPricingRuleStatus.DISABLED);
        assertThat(response.providerCode()).isEqualTo("openai-main");
        assertThat(response.modelName()).isEqualTo("gpt-4.1-mini");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.inputTokenPricePerMillion()).isEqualByComparingTo("0.40000000");
        assertThat(response.outputTokenPricePerMillion()).isEqualByComparingTo("1.60000000");

        var captor = ArgumentCaptor.forClass(AiModelPricingRule.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(AiModelPricingRuleStatus.ACTIVE);
        verify(platformAdminService).requirePlatformAdmin(principal);
    }
}
