package com.archdox.cloud.legal.flow;

import com.archdox.cloud.legal.application.LegalCorpusSyncService;
import com.archdox.cloud.legal.event.LegalSyncRequested;
import com.archdox.cloud.legal.flow.step.FetchLegalSourceSnapshotStep;
import com.archdox.cloud.legal.flow.step.StoreAndDiffLegalSnapshotStep;
import io.github.parkkevinsb.flower.core.flow.Flow;
import org.springframework.stereotype.Component;

@Component
public class LegalSyncFlowFactory {
    public static final String FLOW_TYPE = "legal-sync";

    private final LegalCorpusSyncService syncService;

    public LegalSyncFlowFactory(LegalCorpusSyncService syncService) {
        this.syncService = syncService;
    }

    public Flow create(LegalSyncRequested event) {
        var session = new LegalSyncSession();
        return Flow.builder(FLOW_TYPE, "run:" + event.syncRunId())
                .step("fetch-source-snapshot", new FetchLegalSourceSnapshotStep(syncService, event, session))
                .step("store-and-diff-snapshot", new StoreAndDiffLegalSnapshotStep(syncService, event, session))
                .build();
    }
}
