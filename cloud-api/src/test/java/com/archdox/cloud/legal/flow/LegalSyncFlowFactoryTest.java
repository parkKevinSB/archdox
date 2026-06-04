package com.archdox.cloud.legal.flow;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.legal.application.LegalCorpusSyncService;
import com.archdox.cloud.legal.application.LegalSourceSnapshot;
import com.archdox.cloud.legal.application.LegalSyncResult;
import com.archdox.cloud.legal.event.LegalSyncRequested;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegalSyncFlowFactoryTest {
    @Test
    void fetchesSnapshotThenStoresAndDiffsIt() {
        var service = mock(LegalCorpusSyncService.class);
        var snapshot = new LegalSourceSnapshot(
                "NATIONAL_LAW_FAKE",
                "FAKE",
                "Fake",
                "https://open.law.go.kr",
                Map.of(),
                List.of());
        when(service.fetchSnapshot("NATIONAL_LAW_FAKE")).thenReturn(snapshot);
        when(service.applySnapshot(10L, snapshot)).thenReturn(new LegalSyncResult(10L, 0, 0, 0, 0));
        var worker = worker();

        worker.submit(new LegalSyncFlowFactory(service)
                .create(new LegalSyncRequested(10L, "NATIONAL_LAW_FAKE")));
        worker.tickOnce();
        worker.tickOnce();

        verify(service).fetchSnapshot("NATIONAL_LAW_FAKE");
        verify(service).applySnapshot(10L, snapshot);
    }

    private Worker worker() {
        var worker = Worker.builder("test").build();
        var engine = Engine.builder()
                .worker(worker)
                .build();
        engine.attach();
        return worker;
    }
}
