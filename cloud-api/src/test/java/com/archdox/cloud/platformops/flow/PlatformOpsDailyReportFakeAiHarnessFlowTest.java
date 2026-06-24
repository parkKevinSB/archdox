package com.archdox.cloud.platformops.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.archdox.cloud.aipolicy.application.AiCredentialCipher;
import com.archdox.cloud.aipolicy.application.AiCredentialProperties;
import com.archdox.cloud.aipolicy.application.AiFakeProviderProperties;
import com.archdox.cloud.aipolicy.application.AiFakeResponseFactory;
import com.archdox.cloud.aipolicy.application.AiHarnessExecutionPlan;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyExecutionService;
import com.archdox.cloud.aipolicy.application.AiHarnessPolicyResolution;
import com.archdox.cloud.aipolicy.application.AiModelCallLogService;
import com.archdox.cloud.aipolicy.application.AiSpringAiAdapterProperties;
import com.archdox.cloud.aipolicy.application.ArchDoxProviderAiModelGateway;
import com.archdox.cloud.aipolicy.domain.AiHarnessPolicyKey;
import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.infra.AiModelCallLogRepository;
import com.archdox.cloud.aipolicy.infra.AiProviderCredentialRepository;
import com.archdox.cloud.engine.usage.infra.EngineApiUsageEventRepository;
import com.archdox.cloud.global.event.ArchDoxRuntimeConfiguration;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.infra.OperationEventRepository;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.platformops.application.PlatformOpsDailyReportFindingSink;
import com.archdox.cloud.platformops.application.PlatformOpsDailyReportProperties;
import com.archdox.cloud.platformops.application.PlatformOpsDailyReportRunStore;
import com.archdox.cloud.platformops.application.PlatformOpsDailyReportService;
import com.archdox.cloud.platformops.application.PlatformOpsDiagnosisService;
import com.archdox.cloud.platformops.domain.PlatformOpsDailyReport;
import com.archdox.cloud.platformops.domain.PlatformOpsFinding;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSource;
import com.archdox.cloud.platformops.domain.PlatformOpsRun;
import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRunTriggerType;
import com.archdox.cloud.platformops.event.PlatformOpsDailyReportRequested;
import com.archdox.cloud.platformops.infra.PlatformOpsDailyReportRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsFindingRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsIncidentRepository;
import com.archdox.cloud.platformops.infra.PlatformOpsRunRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.core.engine.Engine;
import io.github.parkkevinsb.flower.core.worker.Worker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class PlatformOpsDailyReportFakeAiHarnessFlowTest {
    @TempDir
    Path tempDir;

    @Test
    void runsDailyReportActionFlowThroughFakeAiHarnessAndWritesReport() throws Exception {
        var platformAdminService = mock(PlatformAdminService.class);
        var runRepository = mock(PlatformOpsRunRepository.class);
        var incidentRepository = mock(PlatformOpsIncidentRepository.class);
        var findingRepository = mock(PlatformOpsFindingRepository.class);
        var dailyReportRepository = mock(PlatformOpsDailyReportRepository.class);
        var operationEventRepository = mock(OperationEventRepository.class);
        var operationEventService = mock(OperationEventService.class);
        var aiModelCallLogRepository = mock(AiModelCallLogRepository.class);
        var engineUsageRepository = mock(EngineApiUsageEventRepository.class);
        var objectMapper = new ObjectMapper();
        var policyExecutionService = mock(AiHarnessPolicyExecutionService.class);
        when(policyExecutionService.resolve(AiHarnessPolicyKey.PLATFORM_OPS_DAILY_REPORT))
                .thenReturn(AiHarnessPolicyResolution.runnable(new AiHarnessExecutionPlan(
                        AiHarnessPolicyKey.PLATFORM_OPS_DAILY_REPORT,
                        mock(AiProviderCredential.class),
                        new ModelId("fake-ops", "fake-ops-model"),
                        1,
                        Duration.ofSeconds(30))));
        var providerRepository = mock(AiProviderCredentialRepository.class);
        var callLogService = mock(AiModelCallLogService.class);
        var credentialProperties = new AiCredentialProperties();
        credentialProperties.setMasterKey("test-master-key");
        var fakeProviderProperties = new AiFakeProviderProperties();
        fakeProviderProperties.setEnabled(true);
        var fakeGateway = new ArchDoxProviderAiModelGateway(
                providerRepository,
                new AiCredentialCipher(credentialProperties),
                callLogService,
                objectMapper,
                fakeProviderProperties,
                new AiFakeResponseFactory(),
                new AiSpringAiAdapterProperties(),
                Optional.empty());
        var properties = new PlatformOpsDailyReportProperties();
        properties.setReportDirectory(tempDir.toString());
        properties.setAutoDiagnosisEnabled(false);
        var aiRunStore = new PlatformOpsDailyReportRunStore(runRepository, operationEventService);
        var aiFindingSink = new PlatformOpsDailyReportFindingSink(runRepository, findingRepository);
        var service = new PlatformOpsDailyReportService(
                platformAdminService,
                properties,
                runRepository,
                incidentRepository,
                findingRepository,
                dailyReportRepository,
                operationEventRepository,
                aiModelCallLogRepository,
                engineUsageRepository,
                operationEventService,
                policyExecutionService,
                mock(PlatformOpsDiagnosisService.class),
                aiRunStore,
                aiFindingSink,
                fakeGateway,
                objectMapper,
                new io.github.parkkevinsb.flower.ai.harness.spi.TraceListener() {});
        var now = OffsetDateTime.parse("2026-06-24T00:00:00+09:00");
        var run = new PlatformOpsRun(
                PlatformOpsRunTriggerType.AUTO_DAILY_REPORT,
                null,
                Map.of(
                        "state", "REQUESTED",
                        "dueAt", now.toString(),
                        "periodFrom", now.minusDays(1).toString(),
                        "periodTo", now.toString()),
                now);
        ReflectionTestUtils.setField(run, "id", 90L);
        var savedFindings = new ArrayList<PlatformOpsFinding>();
        var savedReports = new ArrayList<PlatformOpsDailyReport>();

        when(runRepository.findById(90L)).thenReturn(Optional.of(run));
        when(runRepository.findByAiHarnessRunId(any())).thenAnswer(invocation -> Optional.of(run));
        when(operationEventRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(any(), any(), any()))
                .thenReturn(List.of());
        when(operationEventRepository.summarizeSeverity(any(), any())).thenReturn(List.of());
        when(aiModelCallLogRepository.usageByOfficeAndFeature(any(), any(), any(), any())).thenReturn(List.of());
        when(engineUsageRepository.summarizePlatformUsage(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(findingRepository.summarizeSeverity(any(), any())).thenReturn(List.of());
        when(findingRepository.findBySourceAndWorkflowTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(incidentRepository.findByStatusInOrderByLastSeenAtDescIdDesc(any(), any())).thenReturn(List.of());
        when(findingRepository.save(any(PlatformOpsFinding.class))).thenAnswer(invocation -> {
            var finding = invocation.getArgument(0, PlatformOpsFinding.class);
            savedFindings.add(finding);
            return finding;
        });
        when(findingRepository.findByRunIdOrderByCreatedAtAscIdAsc(90L)).thenAnswer(invocation -> List.copyOf(savedFindings));
        when(dailyReportRepository.findByRunId(90L)).thenReturn(Optional.empty());
        when(dailyReportRepository.save(any(PlatformOpsDailyReport.class))).thenAnswer(invocation -> {
            var report = invocation.getArgument(0, PlatformOpsDailyReport.class);
            savedReports.add(report);
            return report;
        });

        var parentWorker = Worker.builder("platform-ops-parent-test").build();
        var aiWorker = Worker.builder(ArchDoxRuntimeConfiguration.AI_HARNESS_WORKER).build();
        var engine = Engine.builder()
                .worker(parentWorker)
                .worker(aiWorker)
                .build();
        engine.attach();

        parentWorker.submit(new PlatformOpsDailyReportFlowFactory(
                service,
                mock(PlatformOpsDiagnosisFlowFactory.class),
                mock(PlatformOpsWorker.class),
                new PlatformOpsAiHarnessWorker(engine))
                .create(new PlatformOpsDailyReportRequested(90L, now, null)));
        for (int i = 0; i < 40 && run.status() != PlatformOpsRunStatus.COMPLETED; i++) {
            parentWorker.tickOnce();
            aiWorker.tickOnce();
        }

        assertEquals(PlatformOpsRunStatus.COMPLETED, run.status());
        assertNotNull(run.aiHarnessRunId());
        assertTrue(savedFindings.stream().anyMatch(finding -> finding.source() == PlatformOpsFindingSource.AI_HARNESS));
        assertFalse(savedReports.isEmpty());
        assertTrue(Files.exists(Path.of(savedReports.getFirst().reportPath())));
        assertTrue(Files.readString(Path.of(savedReports.getFirst().reportPath())).contains("P-like Current Signals"));
        assertEquals("WATCH", savedReports.getFirst().status());
        assertFalse(savedReports.getFirst().pLikeJson().isEmpty());
        assertFalse(savedReports.getFirst().recommendationsJson().isEmpty());
    }
}
