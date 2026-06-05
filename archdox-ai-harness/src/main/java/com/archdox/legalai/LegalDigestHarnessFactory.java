package com.archdox.legalai;

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

public final class LegalDigestHarnessFactory {
    public static final String HARNESS_ID = "archdox.legal-digest";
    public static final ModelId DEFAULT_MODEL_ID = ModelId.parse("archdox:legal-digest");
    public static final PromptVersion PROMPT_VERSION = new PromptVersion("archdox-legal-digest", "0.1.0");

    private final ObjectMapper objectMapper;

    public LegalDigestHarnessFactory(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public AiHarnessSpec<LegalDigestInput, LegalDigestResult> spec(FindingSink findingSink) {
        return spec(findingSink, AiHarnessRunStore.noop(), new MaxAttemptsRefinePolicy(2));
    }

    public AiHarnessSpec<LegalDigestInput, LegalDigestResult> spec(
            FindingSink findingSink,
            AiHarnessRunStore runStore,
            AiRefinePolicy refinePolicy,
            TraceListener... traceListeners
    ) {
        Objects.requireNonNull(findingSink, "findingSink must not be null");
        Objects.requireNonNull(runStore, "runStore must not be null");
        Objects.requireNonNull(refinePolicy, "refinePolicy must not be null");

        var builder = AiHarnessSpec.<LegalDigestInput, LegalDigestResult>builder()
                .harnessId(HARNESS_ID)
                .defaultModelId(DEFAULT_MODEL_ID)
                .defaultTimeout(Duration.ofSeconds(90))
                .promptVersion(PROMPT_VERSION)
                .promptBuilder(new LegalDigestPromptBuilder(objectMapper))
                .validator(new JacksonPojoSchemaValidator<>(LegalDigestResult.class, objectMapper))
                .refinePolicy(refinePolicy)
                .runStore(runStore)
                .findingExtractor(new LegalDigestFindingExtractor())
                .findingSink(findingSink);

        if (traceListeners != null) {
            for (TraceListener listener : traceListeners) {
                builder.addTraceListener(listener);
            }
        }
        return builder.build();
    }
}
