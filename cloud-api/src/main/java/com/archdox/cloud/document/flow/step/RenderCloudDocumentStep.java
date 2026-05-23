package com.archdox.cloud.document.flow.step;

import com.archdox.cloud.document.application.DocumentGenerationProperties;
import com.archdox.cloud.document.application.DocumentJobService;
import com.archdox.cloud.document.domain.DocumentJobProgressStep;
import com.archdox.cloud.document.event.DocumentGenerationRequested;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import java.util.concurrent.Executor;

public final class RenderCloudDocumentStep extends Step {
    private final DocumentJobService documentJobService;
    private final DocumentGenerationRequested event;
    private final DocumentGenerationStepTask task;

    public RenderCloudDocumentStep(
            DocumentJobService documentJobService,
            DocumentGenerationRequested event,
            Executor executor,
            DocumentGenerationProperties properties
    ) {
        this.documentJobService = documentJobService;
        this.event = event;
        this.task = new DocumentGenerationStepTask("render-cloud-document", event, executor, properties);
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        return task.tick(ctx, () -> {
            documentJobService.markProgress(
                    event.officeId(),
                    event.documentJobId(),
                    DocumentJobProgressStep.RENDERING,
                    25,
                    "문서 렌더링 작업을 시작했습니다.");
            documentJobService.generateCloudDocument(event.officeId(), event.documentJobId());
        });
    }

    @Override
    protected void onExit(StepContext ctx) {
        task.dispose();
    }

    @Override
    protected void onReset(StepContext ctx) {
        task.dispose();
    }
}
