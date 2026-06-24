package com.archdox.cloud.platformops.flow;

import com.archdox.cloud.platformops.application.PlatformOpsDiagnosisService;
import com.archdox.cloud.platformops.event.PlatformOpsDiagnosisRequested;
import com.archdox.cloud.platformops.flow.step.AwaitPlatformOpsAiDiagnosisHarnessStep;
import com.archdox.cloud.platformops.flow.step.BuildPlatformOpsDiagnosisSnapshotStep;
import com.archdox.cloud.platformops.flow.step.SubmitPlatformOpsAiDiagnosisHarnessStep;
import com.archdox.cloud.platformops.flow.step.SummarizePlatformOpsAiDiagnosisStep;
import io.github.parkkevinsb.flower.core.flow.Flow;
import org.springframework.stereotype.Component;

@Component
public class PlatformOpsDiagnosisFlowFactory {
    public static final String FLOW_TYPE = "platform-ops-diagnosis";

    private final PlatformOpsDiagnosisService diagnosisService;
    private final PlatformOpsAiDiagnosisWorker aiDiagnosisWorker;

    public PlatformOpsDiagnosisFlowFactory(
            PlatformOpsDiagnosisService diagnosisService,
            PlatformOpsAiDiagnosisWorker aiDiagnosisWorker
    ) {
        this.diagnosisService = diagnosisService;
        this.aiDiagnosisWorker = aiDiagnosisWorker;
    }

    public Flow create(PlatformOpsDiagnosisRequested event) {
        var targetKey = event.incidentId() == null ? "system" : "incident:" + event.incidentId();
        return Flow.builder(FLOW_TYPE, "run:" + event.opsRunId() + ":" + targetKey)
                .step("build-diagnosis-snapshot", new BuildPlatformOpsDiagnosisSnapshotStep(diagnosisService, event))
                .step("submit-ops-diagnosis-ai-harness", new SubmitPlatformOpsAiDiagnosisHarnessStep(diagnosisService, aiDiagnosisWorker, event))
                .step("await-ops-diagnosis-ai-harness", new AwaitPlatformOpsAiDiagnosisHarnessStep(diagnosisService, event))
                .step("summarize-ops-diagnosis-ai-result", new SummarizePlatformOpsAiDiagnosisStep(diagnosisService, event))
                .build();
    }
}
