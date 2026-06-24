package com.archdox.cloud.platformops.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.platformops.application.PlatformOpsDetectionMonitorDecision;
import com.archdox.cloud.platformops.application.PlatformOpsDetectionMonitorService;
import com.archdox.cloud.platformops.application.PlatformOpsDetectionProperties;
import io.github.parkkevinsb.bloom.LocalEventBus;
import io.github.parkkevinsb.flower.bloom.BloomEventBus;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class PlatformOpsDetectionMonitorFlowFactoryTest {
    @Test
    void monitorFlowLoopsThroughCheckAndWaitSteps() {
        var service = mock(PlatformOpsDetectionMonitorService.class);
        when(service.checkAndRequestIfDue(any(OffsetDateTime.class)))
                .thenReturn(PlatformOpsDetectionMonitorDecision.skipped("NOT_DUE"));
        var properties = new PlatformOpsDetectionProperties();
        properties.setEnabled(true);
        properties.setWorkerIntervalMs(10_000);
        var detectionFlowFactory = mock(PlatformOpsDetectionFlowFactory.class);
        var platformOpsWorker = mock(PlatformOpsWorker.class);
        var clock = new ManualClock();
        var worker = workerWith(clock);
        worker.submit(new PlatformOpsDetectionMonitorFlowFactory(
                service,
                properties,
                detectionFlowFactory,
                platformOpsWorker).create());

        worker.tickOnce();
        verify(service, times(1)).checkAndRequestIfDue(any(OffsetDateTime.class));

        clock.advance(9_999);
        tick(worker, 2);
        verify(service, times(1)).checkAndRequestIfDue(any(OffsetDateTime.class));

        clock.advance(1);
        tick(worker, 2);
        verify(service, times(2)).checkAndRequestIfDue(any(OffsetDateTime.class));
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
