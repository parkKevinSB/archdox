package com.archdox.documentai;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.finding.FindingSink;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.refine.AiRefinePolicy;
import io.github.parkkevinsb.flower.ai.harness.refine.MaxAttemptsRefinePolicy;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunStore;
import io.github.parkkevinsb.flower.ai.harness.spec.AiHarnessSpec;
import io.github.parkkevinsb.flower.ai.harness.spi.TraceListener;
import io.github.parkkevinsb.flower.ai.harness.validator.jackson.JacksonPojoSchemaValidator;
import java.time.Duration;
import java.util.Objects;

public final class DocumentQaHarnessFactory {
    public static final String HARNESS_ID = "archdox.document-qa";
    public static final ModelId DEFAULT_MODEL_ID = ModelId.parse("archdox:document-qa");
    public static final PromptVersion PROMPT_VERSION = new PromptVersion("archdox-document-qa", "1.0.0");

    private final ObjectMapper objectMapper;

    public DocumentQaHarnessFactory(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public AiHarnessSpec<DocumentQaInput, DocumentQaResult> spec(FindingSink findingSink) {
        return spec(findingSink, AiHarnessRunStore.noop(), new MaxAttemptsRefinePolicy(2));
    }

    public AiHarnessSpec<DocumentQaInput, DocumentQaResult> spec(
            FindingSink findingSink,
            AiHarnessRunStore runStore,
            AiRefinePolicy refinePolicy,
            TraceListener... traceListeners
    ) {
        Objects.requireNonNull(findingSink, "findingSink must not be null");
        Objects.requireNonNull(runStore, "runStore must not be null");
        Objects.requireNonNull(refinePolicy, "refinePolicy must not be null");

        var builder = AiHarnessSpec.<DocumentQaInput, DocumentQaResult>builder()
                .harnessId(HARNESS_ID)
                .defaultModelId(DEFAULT_MODEL_ID)
                .defaultTimeout(Duration.ofSeconds(90))
                .promptVersion(PROMPT_VERSION)
                .promptBuilder(new DocumentQaPromptBuilder(objectMapper))
                .validator(new JacksonPojoSchemaValidator<>(DocumentQaResult.class, objectMapper))
                .refinePolicy(refinePolicy)
                .runStore(runStore)
                .findingExtractor(new DocumentQaFindingExtractor())
                .findingSink(findingSink);

        if (traceListeners != null) {
            for (TraceListener listener : traceListeners) {
                builder.addTraceListener(listener);
            }
        }
        return builder.build();
    }
}
