package com.archdox.cloud.document.flow.step;

import com.archdox.cloud.document.application.DocumentGenerationProperties;
import com.archdox.cloud.document.application.DocumentJobService;
import com.archdox.cloud.document.domain.DocumentJobProgressStep;
import com.archdox.cloud.document.event.DocumentGenerationRequested;
import io.github.parkkevinsb.flower.core.step.Step;
import io.github.parkkevinsb.flower.core.step.StepContext;
import io.github.parkkevinsb.flower.core.step.StepResult;
import java.util.concurrent.Executor;

public final class ValidateDocumentJobStep extends Step {
    private final DocumentJobService documentJobService;
    private final DocumentGenerationRequested event;
    private final DocumentGenerationStepTask task;

    public ValidateDocumentJobStep(
            DocumentJobService documentJobService,
            DocumentGenerationRequested event,
            Executor executor,
            DocumentGenerationProperties properties
    ) {
        this.documentJobService = documentJobService;
        this.event = event;
        this.task = new DocumentGenerationStepTask("validate-job", event, executor, properties);
    }

    @Override
    protected StepResult onTick(StepContext ctx) {
        return task.tick(ctx, () -> {
            documentJobService.markProgress(
                    event.officeId(),
                    event.documentJobId(),
                    DocumentJobProgressStep.VALIDATING,
                    10,
                    "문서 생성 조건을 확인하는 중입니다.");
            documentJobService.validateJobReady(event.officeId(), event.documentJobId());
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
