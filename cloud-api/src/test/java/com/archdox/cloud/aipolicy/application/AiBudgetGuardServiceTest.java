package com.archdox.cloud.aipolicy.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.archdox.cloud.aipolicy.domain.AiCredentialDeliveryMode;
import com.archdox.cloud.aipolicy.domain.AiModelPricingRuleStatus;
import com.archdox.cloud.aipolicy.domain.AiUserBudgetOverride;
import com.archdox.cloud.aipolicy.domain.OfficeAiPolicy;
import com.archdox.cloud.aipolicy.infra.AiModelCallLogRepository;
import com.archdox.cloud.aipolicy.infra.AiModelPricingRuleRepository;
import com.archdox.cloud.aipolicy.infra.AiUserBudgetOverrideRepository;
import com.archdox.cloud.global.api.BadRequestException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiBudgetGuardServiceTest {
    private final AiModelCallLogRepository callLogRepository = mock(AiModelCallLogRepository.class);
    private final AiModelPricingRuleRepository pricingRuleRepository = mock(AiModelPricingRuleRepository.class);
    private final AiUserBudgetOverrideRepository overrideRepository = mock(AiUserBudgetOverrideRepository.class);
    private final AiBudgetGuardService service = new AiBudgetGuardService(
            callLogRepository,
            pricingRuleRepository,
            overrideRepository);

    @Test
    void activeUserOverrideAllowsHigherUserDailyLimitThanOfficeDefault() {
        var now = OffsetDateTime.parse("2026-06-09T10:00:00+09:00");
        var policy = policy(now);
        var override = new AiUserBudgetOverride(
                10L,
                20L,
                10,
                null,
                null,
                "USD",
                "Temporary increase",
                now.plusDays(1),
                7L,
                now);
        when(callLogRepository.countByOfficeIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(callLogRepository.sumTokensByOfficeIdAndCompletedAtRange(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(callLogRepository.countByOfficeIdAndUserIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(6L);
        when(callLogRepository.sumTokensByOfficeIdAndUserIdAndCompletedAtRange(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(overrideRepository.findActiveByOfficeIdAndUserId(10L, 20L, now)).thenReturn(List.of(override));

        assertThatCode(() -> service.requireWithinBudget(policy, 20L, "openai-main", "gpt-4.1-mini", now))
                .doesNotThrowAnyException();
    }

    @Test
    void userMonthlyBudgetOverrideBlocksWhenCostLimitIsReached() {
        var now = OffsetDateTime.parse("2026-06-09T10:00:00+09:00");
        var policy = policy(now);
        var override = new AiUserBudgetOverride(
                10L,
                20L,
                null,
                null,
                new BigDecimal("2.00"),
                "USD",
                "Temporary budget cap",
                now.plusDays(1),
                7L,
                now);
        when(callLogRepository.countByOfficeIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(callLogRepository.sumTokensByOfficeIdAndCompletedAtRange(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(callLogRepository.countByOfficeIdAndUserIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(callLogRepository.sumTokensByOfficeIdAndUserIdAndCompletedAtRange(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(overrideRepository.findActiveByOfficeIdAndUserId(10L, 20L, now)).thenReturn(List.of(override));
        when(pricingRuleRepository.existsByProviderCodeAndModelNameAndStatus(
                "openai-main",
                "gpt-4.1-mini",
                AiModelPricingRuleStatus.ACTIVE)).thenReturn(true);
        when(callLogRepository.sumEstimatedCostByOfficeIdAndUserIdAndCurrencyAndCompletedAtRange(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq("USD"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(new BigDecimal("2.00"));

        assertThatThrownBy(() -> service.requireWithinBudget(policy, 20L, "openai-main", "gpt-4.1-mini", now))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Monthly AI budget exceeded for this user");
    }

    private OfficeAiPolicy policy(OffsetDateTime now) {
        var policy = new OfficeAiPolicy(10L, 1L, now);
        policy.update(
                true,
                true,
                true,
                4L,
                AiCredentialDeliveryMode.PROXY_ONLY,
                true,
                null,
                "USD",
                100,
                1_000_000L,
                2_000,
                5,
                500_000L,
                1L,
                now);
        return policy;
    }
}
