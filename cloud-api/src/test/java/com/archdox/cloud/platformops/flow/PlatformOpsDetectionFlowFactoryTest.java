package com.archdox.cloud.platformops.flow;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.archdox.cloud.platformops.application.PlatformOpsDetectionService;
import com.archdox.cloud.platformops.event.PlatformOpsDetectionRequested;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.worker.Worker;
import org.junit.jupiter.api.Test;

class PlatformOpsDetectionFlowFactoryTest {
    @Test
    void runsDeterministicDetectionStep() {
        var service = mock(PlatformOpsDetectionService.class);
        var worker = Worker.builder("test").build();
        var engine = Engine.builder()
                .worker(worker)
                .build();
        engine.attach();

        worker.submit(new PlatformOpsDetectionFlowFactory(service)
                .create(new PlatformOpsDetectionRequested(300L, 1L)));
        worker.tickOnce();

        verify(service).executeStuckDetection(300L);
    }
}
