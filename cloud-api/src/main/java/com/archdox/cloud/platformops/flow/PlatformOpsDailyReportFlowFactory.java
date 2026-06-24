package com.archdox.cloud.platformops.flow;

import com.archdox.cloud.platformops.application.PlatformOpsDailyReportService;
import com.archdox.cloud.platformops.event.PlatformOpsDailyReportRequested;
import com.archdox.cloud.platformops.flow.step.AwaitPlatformOpsDailyReportAiHarnessStep;
import com.archdox.cloud.platformops.flow.step.AwaitPlatformOpsDailyReportAutoDiagnosisStep;
import com.archdox.cloud.platformops.flow.step.BuildPlatformOpsDailyReportSnapshotStep;
import com.archdox.cloud.platformops.flow.step.FinalizePlatformOpsDailyReportStep;
import com.archdox.cloud.platformops.flow.step.RequestPlatformOpsDailyReportAutoDiagnosisStep;
import com.archdox.cloud.platformops.flow.step.SubmitPlatformOpsDailyReportAiHarnessStep;
import io.github.parkkevinsb.flower.core.flow.Flow;
import org.springframework.stereotype.Component;

@Component
public class PlatformOpsDailyReportFlowFactory {
    public static final String FLOW_TYPE = "platform-ops-daily-report";

    private final PlatformOpsDailyReportService service;
    private final PlatformOpsDiagnosisFlowFactory diagnosisFlowFactory;
    private final PlatformOpsWorker platformOpsWorker;
    private final PlatformOpsAiHarnessWorker aiWorker;

    public PlatformOpsDailyReportFlowFactory(
            PlatformOpsDailyReportService service,
            PlatformOpsDiagnosisFlowFactory diagnosisFlowFactory,
            PlatformOpsWorker platformOpsWorker,
            PlatformOpsAiHarnessWorker aiWorker
    ) {
        this.service = service;
        this.diagnosisFlowFactory = diagnosisFlowFactory;
        this.platformOpsWorker = platformOpsWorker;
        this.aiWorker = aiWorker;
    }

    public Flow create(PlatformOpsDailyReportRequested event) {
        return Flow.builder(FLOW_TYPE, "run:" + event.opsRunId())
                .step("request-daily-report-auto-diagnosis", new RequestPlatformOpsDailyReportAutoDiagnosisStep(
                        service,
                        diagnosisFlowFactory,
                        platformOpsWorker,
                        event))
                .step("await-daily-report-auto-diagnosis", new AwaitPlatformOpsDailyReportAutoDiagnosisStep(service, event))
                .step("build-daily-report-evidence-snapshot", new BuildPlatformOpsDailyReportSnapshotStep(service, event))
                .step("submit-ops-daily-report-ai-harness", new SubmitPlatformOpsDailyReportAiHarnessStep(service, aiWorker, event))
                .step("await-ops-daily-report-ai-harness", new AwaitPlatformOpsDailyReportAiHarnessStep(service, event))
                .step("finalize-ops-daily-report", new FinalizePlatformOpsDailyReportStep(service, event))
                .build();
    }
}
