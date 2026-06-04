package com.archdox.cloud.legal.flow.step;

import com.archdox.cloud.legal.application.LegalCorpusSyncService;
import com.archdox.cloud.legal.event.LegalSyncRequested;
import com.archdox.cloud.legal.flow.LegalSyncSession;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;

public final class StoreAndDiffLegalSnapshotStep extends Step {
    private final LegalCorpusSyncService syncService;
    private final LegalSyncRequested event;
    private final LegalSyncSession session;

    public StoreAndDiffLegalSnapshotStep(
            LegalCorpusSyncService syncService,
            LegalSyncRequested event,
            LegalSyncSession session
    ) {
        this.syncService = syncService;
        this.event = event;
        this.session = session;
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        if (session.snapshot() == null) {
            var failure = new IllegalStateException("Legal source snapshot was not fetched");
            syncService.markRunFailed(event.syncRunId(), "LEGAL_SOURCE_SNAPSHOT_MISSING");
            return StepResult.fail(failure);
        }
        try {
            session.result(syncService.applySnapshot(event.syncRunId(), session.snapshot()));
            return StepResult.done();
        } catch (RuntimeException ex) {
            syncService.markRunFailed(event.syncRunId(), ex.getClass().getSimpleName(), ex.getMessage());
            return StepResult.fail(ex);
        }
    }
}
