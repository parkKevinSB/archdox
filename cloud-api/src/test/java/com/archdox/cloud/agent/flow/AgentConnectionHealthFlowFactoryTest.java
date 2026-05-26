package com.archdox.cloud.agent.flow;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.archdox.cloud.agent.application.ArchDoxAgentConnectionHealthProperties;
import com.archdox.cloud.agent.application.ArchDoxAgentConnectionHealthService;
import io.github.parkkevinsb.bloom.LocalEventBus;
import io.github.parkkevinsb.flower.bloom.BloomEventBus;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.Worker;
import org.junit.jupiter.api.Test;

class AgentConnectionHealthFlowFactoryTest {
    @Test
    void monitorFlowLoopsThroughCheckAndWaitSteps() {
        var service = mock(ArchDoxAgentConnectionHealthService.class);
        var properties = new ArchDoxAgentConnectionHealthProperties();
        properties.setCheckIntervalMs(1_000);
        var clock = new ManualClock();
        var worker = workerWith(clock);
        worker.submit(new AgentConnectionHealthFlowFactory(service, properties).create());

        worker.tickOnce();
        verify(service, times(1)).disconnectHeartbeatTimedOutSessions();

        clock.advance(999);
        tick(worker, 2);
        verify(service, times(1)).disconnectHeartbeatTimedOutSessions();

        clock.advance(1);
        tick(worker, 2);
        verify(service, times(2)).disconnectHeartbeatTimedOutSessions();
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
