package com.archdox.cloud.legal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.legal.domain.LegalSyncRun;
import com.archdox.cloud.legal.domain.LegalSyncRunStatus;
import com.archdox.cloud.legal.flow.LegalSyncFlowFactory;
import com.archdox.cloud.legal.flow.LegalSyncWorker;
import com.archdox.cloud.legal.infra.LegalSyncRunRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import io.github.parkkevinsb.flower.core.flow.Flow;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class LegalSyncMonitorServiceTest {
    private final LegalSyncProperties properties = readyProperties();
    private final LegalSyncRunRepository repository = mock(LegalSyncRunRepository.class);
    private final LegalCorpusSyncService syncService = mock(LegalCorpusSyncService.class);
    private final LegalSyncFlowFactory flowFactory = mock(LegalSyncFlowFactory.class);
    private final LegalSyncWorker worker = mock(LegalSyncWorker.class);
    private final OperationEventService operationEventService = mock(OperationEventService.class);
    private final LegalSyncMonitorService service = new LegalSyncMonitorService(
            properties,
            repository,
            syncService,
            flowFactory,
            worker,
            operationEventService);

    @Test
    void submitsOpenDataSyncWhenDueSlotHasNotBeenHandled() {
        var now = OffsetDateTime.parse("2026-06-10T06:00:00Z");
        var run = mock(LegalSyncRun.class);
        var flow = mock(Flow.class);
        when(repository.existsBySourceCodeAndStatus("NATIONAL_LAW_OPEN_DATA", LegalSyncRunStatus.RUNNING)).thenReturn(false);
        when(repository.findFirstBySourceCodeOrderByStartedAtDescIdDesc("NATIONAL_LAW_OPEN_DATA")).thenReturn(Optional.empty());
        when(run.id()).thenReturn(11L);
        when(run.sourceCode()).thenReturn("NATIONAL_LAW_OPEN_DATA");
        when(syncService.createRun(LegalSyncMonitorService.TRIGGER_TYPE, "NATIONAL_LAW_OPEN_DATA", null)).thenReturn(run);
        when(flowFactory.create(any())).thenReturn(flow);

        var decision = service.checkAndSubmitIfDue(now);

        assertThat(decision.status()).isEqualTo("SUBMITTED");
        assertThat(decision.syncRunId()).isEqualTo(11L);
        verify(worker).submit(flow);
    }

    @Test
    void skipsWhenLatestRunAlreadyHandledDueSlot() {
        var now = OffsetDateTime.parse("2026-06-10T06:30:00Z");
        var latest = mock(LegalSyncRun.class);
        when(repository.existsBySourceCodeAndStatus("NATIONAL_LAW_OPEN_DATA", LegalSyncRunStatus.RUNNING)).thenReturn(false);
        when(latest.startedAt()).thenReturn(OffsetDateTime.parse("2026-06-10T06:05:00Z"));
        when(repository.findFirstBySourceCodeOrderByStartedAtDescIdDesc("NATIONAL_LAW_OPEN_DATA")).thenReturn(Optional.of(latest));

        var decision = service.checkAndSubmitIfDue(now);

        assertThat(decision.status()).isEqualTo("SKIPPED");
        assertThat(decision.reason()).isEqualTo("DUE_SLOT_ALREADY_HANDLED");
        verify(syncService, never()).createRun(any(), any(), any());
        verify(worker, never()).submit(any());
    }

    @Test
    void skipsWhenRunningRunIsCreatedByAnotherRequestAfterCheck() {
        var now = OffsetDateTime.parse("2026-06-10T06:00:00Z");
        when(repository.existsBySourceCodeAndStatus("NATIONAL_LAW_OPEN_DATA", LegalSyncRunStatus.RUNNING)).thenReturn(false);
        when(repository.findFirstBySourceCodeOrderByStartedAtDescIdDesc("NATIONAL_LAW_OPEN_DATA")).thenReturn(Optional.empty());
        when(syncService.createRun(LegalSyncMonitorService.TRIGGER_TYPE, "NATIONAL_LAW_OPEN_DATA", null))
                .thenThrow(new DataIntegrityViolationException("running sync already exists"));

        var decision = service.checkAndSubmitIfDue(now);

        assertThat(decision.status()).isEqualTo("SKIPPED");
        assertThat(decision.reason()).isEqualTo("SYNC_ALREADY_RUNNING");
        verify(worker, never()).submit(any());
    }

    private LegalSyncProperties readyProperties() {
        var props = new LegalSyncProperties();
        props.getMonitor().setEnabled(true);
        props.getMonitor().setRunTimes("03:00,15:00");
        props.getMonitor().setCatchUpGraceMinutes(120);
        props.getOpenApi().setEnabled(true);
        props.getOpenApi().setOc("test-oc");
        return props;
    }
}
