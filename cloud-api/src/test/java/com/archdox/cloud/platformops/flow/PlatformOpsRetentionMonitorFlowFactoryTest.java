package com.archdox.cloud.platformops.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.platformops.application.PlatformOpsRetentionResult;
import com.archdox.cloud.platformops.application.PlatformOpsRetentionService;
import io.github.parkkevinsb.bloom.LocalEventBus;
import io.github.parkkevinsb.flower.bloom.BloomEventBus;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class PlatformOpsRetentionMonitorFlowFactoryTest {
    @Test
    void monitorFlowLoopsThroughCheckAndWaitSteps() {
        var service = mock(PlatformOpsRetentionService.class);
        when(service.enabled()).thenReturn(true);
        when(service.checkIntervalMs()).thenReturn(10_000L);
        when(service.purgeExpired(any(OffsetDateTime.class)))
                .thenReturn(PlatformOpsRetentionResult.disabled(30, OffsetDateTime.parse("2026-05-26T00:00:00Z")));
        var clock = new ManualClock();
        var worker = workerWith(clock);
        worker.submit(new PlatformOpsRetentionMonitorFlowFactory(service).create());

        worker.tickOnce();
        verify(service, times(1)).purgeExpired(any(OffsetDateTime.class));

        clock.advance(9_999);
        tick(worker, 2);
        verify(service, times(1)).purgeExpired(any(OffsetDateTime.class));

        clock.advance(1);
        tick(worker, 2);
        verify(service, times(2)).purgeExpired(any(OffsetDateTime.class));
    }

    private Worker workerWith(ManualClock clock) {
        var worker = Worker.builder("test").build();
        var engine = Engine.builder()
                .clock(clock)
                .eventBus(BloomEventBus.wrap(LocalEventBus.create()))
                .worker(worker)
                .build();
        engine.attach();
        return worker;
    }

    private void tick(Worker worker, int count) {
        for (int i = 0; i < count; i++) {
            worker.tickOnce();
        }
    }
}
