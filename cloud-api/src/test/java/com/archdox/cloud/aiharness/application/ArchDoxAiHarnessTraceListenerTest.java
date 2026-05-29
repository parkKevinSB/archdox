package com.archdox.cloud.aiharness.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.archdox.cloud.aipolicy.application.AiModelCallMetadata;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ArchDoxAiHarnessTraceListenerTest {
    @Test
    void recordsRequestSubmittedWithoutPromptBody() {
        var service = mock(AiHarnessTraceEventService.class);
        var listener = new ArchDoxAiHarnessTraceListener(service);
        var promptVersion = new PromptVersion("archdox-document-qa", "1.0.0");
        var context = new AiHarnessRunContext(
                new AiHarnessRunId("harness-run-1"),
                "archdox.document-qa",
                promptVersion,
                Instant.parse("2026-05-29T00:00:00Z"));
        var prompt = new RenderedPrompt(
                List.of(new RenderedPrompt.Message(RenderedPrompt.Role.USER, "sensitive prompt body")),
                promptVersion);
        var request = new AiModelRequest(
                ModelId.parse("fake:qa-model"),
                prompt,
                AiModelCallMetadata.options(
                        7L,
                        "DOCUMENT_REVIEW",
                        "document-ai-review",
                        "document-job:10",
                        "DOCUMENT_JOB",
                        10L,
                        Map.of()),
                Duration.ofSeconds(30));

        listener.onRequestSubmitted(context, request, "call-1");

        var captor = ArgumentCaptor.forClass(AiHarnessTraceEventCommand.class);
        verify(service).record(captor.capture());
        var command = captor.getValue();
        assertThat(command.officeId()).isEqualTo(7L);
        assertThat(command.harnessRunId()).isEqualTo("harness-run-1");
        assertThat(command.harnessId()).isEqualTo("archdox.document-qa");
        assertThat(command.eventType()).isEqualTo("REQUEST_SUBMITTED");
        assertThat(command.modelId()).isEqualTo("fake:qa-model");
        assertThat(command.callId()).isEqualTo("call-1");
        assertThat(command.promptId()).isEqualTo("archdox-document-qa");
        assertThat(command.attributes()).containsEntry("archdox.workflowType", "document-ai-review");
        assertThat(command.attributes().toString()).doesNotContain("sensitive prompt body");
    }
}
