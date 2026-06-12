package com.archdox.cloud.monitoring.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.monitoring.domain.ServerRuntimeHealthSettings;
import com.archdox.cloud.monitoring.infra.ServerRuntimeHealthSettingsRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ServerRuntimeHealthServiceTest {
    private final OperationEventService operationEventService = mock(OperationEventService.class);
    private final ServerRuntimeHealthSettingsRepository settingsRepository = mock(ServerRuntimeHealthSettingsRepository.class);

    @Test
    void normalSampleDoesNotRecordOperationEvent() {
        var now = OffsetDateTime.parse("2026-06-12T12:00:00+09:00");
        var service = service(capturedAt -> new ServerRuntimeHealthMetrics(
                capturedAt,
                10.0d,
                5.0d,
                0.3d,
                2,
                1_000L,
                700L,
                700L,
                1_000L,
                200L));

        var snapshot = service.sample(now, true);

        org.assertj.core.api.Assertions.assertThat(snapshot.status()).isEqualTo("OK");
        verify(operationEventService, never()).record(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any());
    }

    @Test
    void highLoadEventIsRecordedWithCooldown() {
        var now = OffsetDateTime.parse("2026-06-12T12:00:00+09:00");
        var service = service(capturedAt -> new ServerRuntimeHealthMetrics(
                capturedAt,
                95.0d,
                91.0d,
                2.5d,
                2,
                1_000L,
                50L,
                50L,
                1_000L,
                950L));

        var first = service.sample(now, true);
        var second = service.sample(now.plusSeconds(30), true);

        org.assertj.core.api.Assertions.assertThat(first.status()).isEqualTo("WARN");
        org.assertj.core.api.Assertions.assertThat(second.warnings())
                .contains("CPU_LOAD_HIGH", "SYSTEM_MEMORY_HIGH", "JVM_HEAP_HIGH");
        verify(operationEventService, times(1)).record(
                isNull(),
                eq(OperationEventSeverity.WARN),
                eq(ServerRuntimeHealthService.EVENT_TYPE_LOAD_HIGH),
                eq(ServerRuntimeHealthService.WORKFLOW_TYPE),
                eq(ServerRuntimeHealthService.WORKFLOW_KEY),
                eq("SERVER_RUNTIME"),
                eq("local"),
                any(),
                any());
    }

    @Test
    void availableMemoryIsPreferredForSystemMemoryPressure() {
        var now = OffsetDateTime.parse("2026-06-12T12:00:00+09:00");
        var service = service(capturedAt -> new ServerRuntimeHealthMetrics(
                capturedAt,
                10.0d,
                5.0d,
                0.3d,
                2,
                1_000L,
                50L,
                400L,
                1_000L,
                200L));

        var snapshot = service.sample(now, true);

        org.assertj.core.api.Assertions.assertThat(snapshot.systemMemoryUsedBytes()).isEqualTo(600L);
        org.assertj.core.api.Assertions.assertThat(snapshot.systemMemoryAvailableBytes()).isEqualTo(400L);
        org.assertj.core.api.Assertions.assertThat(snapshot.warnings()).doesNotContain("SYSTEM_MEMORY_HIGH");
    }

    private ServerRuntimeHealthService service(ServerRuntimeHealthProbe probe) {
        var properties = new ServerRuntimeHealthProperties();
        properties.setCpuWarnPercent(85.0d);
        properties.setSystemMemoryWarnPercent(90.0d);
        properties.setJvmHeapWarnPercent(90.0d);
        properties.setEventCooldownMs(60_000);
        when(settingsRepository.findById(ServerRuntimeHealthSettings.SINGLETON_KEY)).thenReturn(Optional.empty());
        return new ServerRuntimeHealthService(properties, probe, operationEventService, settingsRepository);
    }
}
