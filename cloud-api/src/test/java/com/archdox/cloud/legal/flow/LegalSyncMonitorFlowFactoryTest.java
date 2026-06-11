package com.archdox.cloud.legal.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.legal.application.LegalSyncMonitorDecision;
import com.archdox.cloud.legal.application.LegalSyncMonitorService;
import com.archdox.cloud.legal.application.LegalSyncProperties;
import io.github.parkkevinsb.bloom.LocalEventBus;
import io.github.parkkevinsb.flower.bloom.BloomEventBus;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class LegalSyncMonitorFlowFactoryTest {
    @Test
    void monitorFlowLoopsThroughCheckAndWaitSteps() {
        var service = mock(LegalSyncMonitorService.class);
        when(service.checkAndSubmitIfDue(any(OffsetDateTime.class)))
                .thenReturn(LegalSyncMonitorDecision.skipped("NOT_DUE", "NATIONAL_LAW_OPEN_DATA", null));
        var properties = new LegalSyncProperties();
        properties.getMonitor().setEnabled(true);
        properties.getMonitor().setCheckIntervalMs(1_000);
        var clock = new ManualClock();
        var worker = workerWith(clock);
        worker.submit(new LegalSyncMonitorFlowFactory(service, properties).create());

        worker.tickOnce();
        verify(service, times(1)).checkAndSubmitIfDue(any(OffsetDateTime.class));

        clock.advance(999);
        tick(worker, 2);
        verify(service, times(1)).checkAndSubmitIfDue(any(OffsetDateTime.class));

        clock.advance(1);
        tick(worker, 2);
        verify(service, times(2)).checkAndSubmitIfDue(any(OffsetDateTime.class));
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
