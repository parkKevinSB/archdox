package com.archdox.cloud.platformops.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.infra.AiModelCallLogRepository;
import com.archdox.cloud.engine.usage.infra.EngineApiUsageEventRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.infra.OperationEventRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsIncident;
import com.archdox.cloud.platformops.domain.PlatformOpsRun;
import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRunTriggerType;
import com.archdox.cloud.platformops.infra.PlatformOpsDailyReportRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsFindingRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsIncidentRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class PlatformOpsDailyReportServiceAutoDiagnosisTest {
    private final PlatformOpsRunRepository runRepository = mock(PlatformOpsRunRepository.class);
    private final PlatformOpsIncidentRepository incidentRepository = mock(PlatformOpsIncidentRepository.class);
    private final PlatformOpsFindingRepository findingRepository = mock(PlatformOpsFindingRepository.class);
    private final PlatformOpsDiagnosisService diagnosisService = mock(PlatformOpsDiagnosisService.class);
    private final OperationEventService operationEventService = mock(OperationEventService.class);

    @Test
    void requestsSystemDiagnosisAndRepresentativeIncidentDiagnosisBeforeDailyReport() {
        var now = OffsetDateTime.parse("2026-06-25T00:00:00+09:00");
        var reportRun = new PlatformOpsRun(
                PlatformOpsRunTriggerType.AUTO_DAILY_REPORT,
                null,
                Map.of("state", "REQUESTED"),
                now);
        ReflectionTestUtils.setField(reportRun, "id", 10L);
        var olderDuplicateIncident = new PlatformOpsIncident(
                PlatformOpsFindingSeverity.WARN,
                "DOCUMENT_JOB_STUCK",
                "Document job appears stuck",
                "Document job did not progress.",
                1L,
                "DOCUMENT_JOB",
                "99",
                9L,
                now.minusMinutes(10));
        ReflectionTestUtils.setField(olderDuplicateIncident, "id", 20L);
        var representativeIncident = new PlatformOpsIncident(
                PlatformOpsFindingSeverity.ERROR,
                "DOCUMENT_JOB_STUCK",
                "Document job still appears stuck",
                "Document job did not progress after detector recheck.",
                1L,
                "DOCUMENT_JOB",
                "99",
                9L,
                now.minusMinutes(5));
        ReflectionTestUtils.setField(representativeIncident, "id", 21L);
        var systemRun = new PlatformOpsRun(
                PlatformOpsRunTriggerType.AUTO_SYSTEM_DIAGNOSIS,
                null,
                Map.of("state", "REQUESTED"),
                now);
        ReflectionTestUtils.setField(systemRun, "id", 29L);
        var diagnosisRun = new PlatformOpsRun(
                PlatformOpsRunTriggerType.AUTO_DIAGNOSIS,
                null,
                Map.of("state", "REQUESTED"),
                now);
        ReflectionTestUtils.setField(diagnosisRun, "id", 30L);
        diagnosisRun.attachIncident(21L);

        when(runRepository.findById(10L)).thenReturn(Optional.of(reportRun));
        when(incidentRepository.findByStatusInOrderByLastSeenAtDescIdDesc(anyList(), any(Pageable.class)))
                .thenReturn(List.of(representativeIncident, olderDuplicateIncident));
        when(diagnosisService.requestAutoSystemDiagnosis(eq(10L), any())).thenReturn(systemRun);
        when(diagnosisService.requestAutoIncidentDiagnosis(eq(21L), eq(10L), any())).thenReturn(diagnosisRun);

        var runs = service().requestAutoDiagnosesBeforeReport(10L);

        assertThat(runs).extracting(PlatformOpsRun::id).containsExactly(29L, 30L);
        var autoDiagnosis = reportRun.inputSnapshotJson().get("autoDiagnosis");
        assertThat(autoDiagnosis).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) autoDiagnosis).get("status")).isEqualTo("REQUESTED");
        assertThat(((Map<?, ?>) autoDiagnosis).get("systemDiagnosisRunId")).isEqualTo(29L);
        assertThat(((Map<?, ?>) autoDiagnosis).get("requestedCount")).isEqualTo(2);
        assertThat(((Map<?, ?>) autoDiagnosis).get("representativeIncidentCount")).isEqualTo(1);
        verify(diagnosisService).requestAutoIncidentDiagnosis(eq(21L), eq(10L), any());
        verify(diagnosisService, never()).requestAutoIncidentDiagnosis(eq(20L), eq(10L), any());
    }

    private PlatformOpsDailyReportService service() {
        var properties = new PlatformOpsDailyReportProperties();
        properties.setAutoDiagnosisEnabled(true);
        properties.setAutoDiagnosisIncidentLimit(1);
        return new PlatformOpsDailyReportService(
                mock(PlatformAdminService.class),
                properties,
                runRepository,
                incidentRepository,
                findingRepository,
                mock(PlatformOpsDailyReportRepository.class),
                mock(OperationEventRepository.class),
                mock(AiModelCallLogRepository.class),
                mock(EngineApiUsageEventRepository.class),
                operationEventService,
                mock(AiHarnessPolicyExecutionService.class),
                diagnosisService,
                mock(PlatformOpsDailyReportRunStore.class),
                mock(PlatformOpsDailyReportFindingSink.class),
                mock(AiModelGateway.class),
                new ObjectMapper(),
                new io.github.parkkevinsb.flower.ai.harness.spi.TraceListener() {});
    }
}
