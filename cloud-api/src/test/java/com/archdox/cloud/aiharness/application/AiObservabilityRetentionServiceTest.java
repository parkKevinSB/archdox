package com.archdox.cloud.aiharness.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.aiharness.infra.AiHarnessTraceEventRepository;
import com.archdox.cloud.aipolicy.infra.AiModelCallLogRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AiObservabilityRetentionServiceTest {
    private final AiObservabilityRetentionProperties properties = new AiObservabilityRetentionProperties();
    private final AiHarnessTraceEventRepository traceEventRepository = mock(AiHarnessTraceEventRepository.class);
    private final AiModelCallLogRepository modelCallLogRepository = mock(AiModelCallLogRepository.class);

    @Test
    void purgesTraceAndModelCallLogsOlderThanRetentionDays() {
        var now = OffsetDateTime.parse("2026-06-20T00:00:00Z");
        var cutoff = OffsetDateTime.parse("2026-05-21T00:00:00Z");
        when(traceEventRepository.deleteCreatedBefore(cutoff)).thenReturn(12);
        when(modelCallLogRepository.deleteCompletedBefore(cutoff)).thenReturn(7);

        var result = service().purgeExpired(now);

        assertThat(result.enabled()).isTrue();
        assertThat(result.retentionDays()).isEqualTo(30);
        assertThat(result.cutoff()).isEqualTo(cutoff);
        assertThat(result.deletedTraceEvents()).isEqualTo(12);
        assertThat(result.deletedModelCallLogs()).isEqualTo(7);
        verify(traceEventRepository).deleteCreatedBefore(cutoff);
        verify(modelCallLogRepository).deleteCompletedBefore(cutoff);
    }

    @Test
    void disabledRetentionDoesNotDeleteLogs() {
        properties.setEnabled(false);
        var now = OffsetDateTime.parse("2026-06-20T00:00:00Z");

        var result = service().purgeExpired(now);

        assertThat(result.enabled()).isFalse();
        assertThat(result.retentionDays()).isEqualTo(30);
        assertThat(result.cutoff()).isEqualTo(OffsetDateTime.parse("2026-05-21T00:00:00Z"));
        assertThat(result.deletedTraceEvents()).isZero();
        assertThat(result.deletedModelCallLogs()).isZero();
        verify(traceEventRepository, never()).deleteCreatedBefore(result.cutoff());
        verify(modelCallLogRepository, never()).deleteCompletedBefore(result.cutoff());
    }

    @Test
    void checkIntervalAndRetentionDaysHaveSafeMinimums() {
        properties.setRetentionDays(0);
        properties.setCheckIntervalMs(1);

        assertThat(service().purgeExpired(OffsetDateTime.parse("2026-06-20T00:00:00Z")).retentionDays())
                .isEqualTo(1);
        assertThat(service().checkIntervalMs()).isEqualTo(60_000);
    }

    private AiObservabilityRetentionService service() {
        return new AiObservabilityRetentionService(properties, traceEventRepository, modelCallLogRepository);
    }
}
