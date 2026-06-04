package com.archdox.cloud.engine.usage.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.engine.auth.application.EngineApiPrincipal;
import com.archdox.cloud.engine.usage.infra.EngineApiUsageEventRepository;
import com.archdox.cloud.global.api.TooManyRequestsException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class EngineApiQuotaGuardServiceTest {
    private final EngineApiUsageEventRepository repository = mock(EngineApiUsageEventRepository.class);
    private final EngineApiQuotaGuardService service = new EngineApiQuotaGuardService(repository);

    @Test
    void allowsRequestWhenDailyQuotaHasRemainingUnits() {
        var principal = principal(10);
        when(repository.sumRequestUnitsForApiKey(
                eq(11L),
                eq(EngineApiUsageService.CAPABILITY_REVIEW_SESSION),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)))
                .thenReturn(9L);

        service.requireReviewSessionQuota(principal, "RUN_VALIDATION");

        verify(repository).sumRequestUnitsForApiKey(
                eq(11L),
                eq(EngineApiUsageService.CAPABILITY_REVIEW_SESSION),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class));
    }

    @Test
    void rejectsRequestWhenDailyQuotaWouldBeExceeded() {
        var principal = principal(10);
        when(repository.sumRequestUnitsForApiKey(
                eq(11L),
                eq(EngineApiUsageService.CAPABILITY_REVIEW_SESSION),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)))
                .thenReturn(10L);

        assertThatThrownBy(() -> service.requireReviewSessionQuota(principal, "RUN_VALIDATION"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Engine API daily quota exceeded");
    }

    private EngineApiPrincipal principal(Integer dailyRequestUnitLimit) {
        return new EngineApiPrincipal(
                11L,
                "key_test",
                22L,
                33L,
                List.of("ENGINE_REVIEW_SESSION"),
                dailyRequestUnitLimit);
    }
}
