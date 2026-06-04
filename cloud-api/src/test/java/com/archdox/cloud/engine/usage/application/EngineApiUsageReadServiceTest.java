package com.archdox.cloud.engine.usage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.engine.usage.domain.EngineApiUsageEvent;
import com.archdox.cloud.engine.usage.infra.EngineApiUsageEventRepository;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.platformadmin.domain.PlatformAdmin;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class EngineApiUsageReadServiceTest {
    private final EngineApiUsageEventRepository repository = mock(EngineApiUsageEventRepository.class);
    private final PlatformAdminService platformAdminService = mock(PlatformAdminService.class);
    private final EngineApiUsageReadService service = new EngineApiUsageReadService(repository, platformAdminService);
    private final UserPrincipal principal = new UserPrincipal(7L, "platform@test.co.kr");

    @Test
    void returnsUsageEventsForPlatformAdmin() {
        when(platformAdminService.requirePlatformAdmin(principal)).thenReturn(mock(PlatformAdmin.class));
        var from = OffsetDateTime.parse("2026-06-01T00:00:00+09:00");
        var to = OffsetDateTime.parse("2026-06-02T00:00:00+09:00");
        when(repository.searchPlatformUsageEvents(
                eq(11L),
                eq(22L),
                eq("ENGINE_REVIEW_SESSION"),
                eq("RUN_VALIDATION"),
                eq("rvw_sess_001"),
                eq(from),
                eq(to),
                any(Pageable.class)))
                .thenReturn(List.of(new EngineApiUsageEvent(
                        11L,
                        "key_test",
                        33L,
                        22L,
                        "ENGINE_REVIEW_SESSION",
                        "RUN_VALIDATION",
                        "rvw_sess_001",
                        "SUCCEEDED",
                        1,
                        Map.of("findingCount", 2),
                        from.plusHours(1))));

        var events = service.events(
                principal,
                11L,
                22L,
                "ENGINE_REVIEW_SESSION",
                "RUN_VALIDATION",
                "rvw_sess_001",
                from,
                to,
                10);

        verify(platformAdminService).requirePlatformAdmin(principal);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.apiKeyId()).isEqualTo(11L);
            assertThat(event.keyId()).isEqualTo("key_test");
            assertThat(event.ownerUserId()).isEqualTo(33L);
            assertThat(event.officeId()).isEqualTo(22L);
            assertThat(event.operation()).isEqualTo("RUN_VALIDATION");
            assertThat(event.metadata()).containsEntry("findingCount", 2);
        });
    }

    @Test
    void returnsUsageSummaryForPlatformAdmin() {
        when(platformAdminService.requirePlatformAdmin(principal)).thenReturn(mock(PlatformAdmin.class));
        var from = OffsetDateTime.parse("2026-06-01T00:00:00+09:00");
        var to = OffsetDateTime.parse("2026-06-30T00:00:00+09:00");
        when(repository.summarizePlatformUsage(
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(from),
                eq(to)))
                .thenReturn(List.of(summary(11L, "key_test", 33L, 22L, "RUN_VALIDATION", 3L, 3L, to.minusDays(1))));

        var summary = service.summary(principal, null, null, null, null, from, to);

        assertThat(summary.totalEventCount()).isEqualTo(3L);
        assertThat(summary.totalRequestUnits()).isEqualTo(3L);
        assertThat(summary.groups()).singleElement()
                .satisfies(group -> assertThat(group.operation()).isEqualTo("RUN_VALIDATION"));
    }

    @Test
    void rejectsInvalidDateRange() {
        when(platformAdminService.requirePlatformAdmin(principal)).thenReturn(mock(PlatformAdmin.class));
        var from = OffsetDateTime.parse("2026-06-02T00:00:00+09:00");
        var to = OffsetDateTime.parse("2026-06-01T00:00:00+09:00");

        assertThatThrownBy(() -> service.summary(principal, null, null, null, null, from, to))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("to must be after from");
    }

    private EngineApiUsageEventRepository.EngineApiUsageSummaryProjection summary(
            Long apiKeyId,
            String keyId,
            Long ownerUserId,
            Long officeId,
            String operation,
            Long eventCount,
            Long requestUnits,
            OffsetDateTime lastCalledAt
    ) {
        return new EngineApiUsageEventRepository.EngineApiUsageSummaryProjection() {
            @Override
            public Long getApiKeyId() {
                return apiKeyId;
            }

            @Override
            public String getKeyId() {
                return keyId;
            }

            @Override
            public Long getOwnerUserId() {
                return ownerUserId;
            }

            @Override
            public Long getOfficeId() {
                return officeId;
            }

            @Override
            public String getCapability() {
                return "ENGINE_REVIEW_SESSION";
            }

            @Override
            public String getOperation() {
                return operation;
            }

            @Override
            public Long getEventCount() {
                return eventCount;
            }

            @Override
            public Long getRequestUnits() {
                return requestUnits;
            }

            @Override
            public OffsetDateTime getLastCalledAt() {
                return lastCalledAt;
            }
        };
    }
}
