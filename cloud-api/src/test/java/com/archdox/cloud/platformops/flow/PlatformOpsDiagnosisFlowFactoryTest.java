package com.archdox.cloud.platformops.flow;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.platformops.application.PlatformOpsDiagnosisService;
import com.archdox.cloud.platformops.event.PlatformOpsDiagnosisRequested;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlatformOpsDiagnosisFlowFactoryTest {
    @Test
    void runsDiagnosisSnapshotAndOptionalAiHarnessSteps() {
        var service = mock(PlatformOpsDiagnosisService.class);
        var aiWorker = mock(PlatformOpsAiDiagnosisWorker.class);
        when(service.createAiDiagnosisHarnessFlow(300L)).thenReturn(Optional.empty());
        when(service.isAiHarnessTerminal(300L)).thenReturn(true);
        var worker = Worker.builder("test").build();
        var engine = Engine.builder()
                .worker(worker)
                .build();
        engine.attach();

        worker.submit(new PlatformOpsDiagnosisFlowFactory(service, aiWorker)
                .create(new PlatformOpsDiagnosisRequested(300L, 55L, 1L)));
        for (int i = 0; i < 4; i++) {
            worker.tickOnce();
        }

        verify(service).buildIncidentDiagnosisSnapshot(300L);
        verify(service).createAiDiagnosisHarnessFlow(300L);
        verify(service).isAiHarnessTerminal(300L);
        verify(service).summarizeAiDiagnosis(300L);
    }
}
