package com.archdox.cloud.aipolicy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.domain.AiProviderType;
import com.archdox.cloud.aipolicy.infra.AiProviderCredentialRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.parkkevinsb.flower.ai.harness.gateway.GatewayException;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCall;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCallStatus;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.model.ProviderOptions;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import io.github.parkkevinsb.flower.ai.harness.springai.SpringAiModelGateway;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class ArchDoxProviderAiModelGatewayTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void submitsOpenAiCompatibleRequestUsingProviderCodeFromModelId() throws Exception {
        var capturedRequestBody = new AtomicReference<String>();
        startServer("/chat/completions", """
                {
                  "id": "chatcmpl-test",
                  "choices": [
                    {"message": {"content": "{\\"status\\":\\"PASS\\"}"}, "finish_reason": "stop"}
                  ],
                  "usage": {"prompt_tokens": 11, "completion_tokens": 7}
                }
                """, capturedRequestBody);
        var provider = publishedProvider(
                "local-openai",
                AiProviderType.CUSTOM_HTTP,
                "http://localhost:" + server.getAddress().getPort(),
                "gpt-test");
        var gateway = gateway(provider, "local-openai");

        var call = gateway.submit(request("local-openai", "gpt-test"));
        var response = await(call);

        assertThat(response.rawText()).isEqualTo("{\"status\":\"PASS\"}");
        assertThat(response.modelId().asString()).isEqualTo("local-openai:gpt-test");
        assertThat(response.metadata().inputTokens()).contains(11);
        assertThat(response.metadata().outputTokens()).contains(7);
        assertThat(capturedRequestBody.get()).contains("\"model\":\"gpt-test\"");
        assertThat(capturedRequestBody.get()).contains("\"role\":\"system\"");
        assertThat(capturedRequestBody.get()).contains("\"role\":\"user\"");
    }

    @Test
    void connectionTestCanCallDraftProviderWithoutPublishingIt() throws Exception {
        startServer("/chat/completions", """
                {
                  "id": "chatcmpl-test",
                  "choices": [
                    {"message": {"content": "{\\"status\\":\\"ok\\"}"}, "finish_reason": "stop"}
                  ],
                  "usage": {"prompt_tokens": 4, "completion_tokens": 3}
                }
                """, new AtomicReference<>());
        var provider = new AiProviderCredential(
                "draft-openai",
                "Draft OpenAI",
                AiProviderType.CUSTOM_HTTP,
                "http://localhost:" + server.getAddress().getPort(),
                "gpt-test",
                null,
                null,
                1L,
                OffsetDateTime.now());
        var gateway = gateway(provider, "draft-openai");

        var response = await(gateway.submit(connectionTestRequest("draft-openai", "gpt-test")));

        assertThat(response.rawText()).isEqualTo("{\"status\":\"ok\"}");
        assertThat(response.modelId().asString()).isEqualTo("draft-openai:gpt-test");
    }

    @Test
    void submitsOllamaChatRequestUsingProviderCodeFromModelId() throws Exception {
        startServer("/api/chat", """
                {
                  "message": {"content": "{\\"status\\":\\"PASS\\",\\"provider\\":\\"ollama\\"}"},
                  "prompt_eval_count": 3,
                  "eval_count": 5,
                  "done_reason": "stop"
                }
                """, new AtomicReference<>());
        var provider = publishedProvider(
                "local-ollama",
                AiProviderType.OLLAMA,
                "http://localhost:" + server.getAddress().getPort(),
                "llama3.1");
        var gateway = gateway(provider, "local-ollama");

        var response = await(gateway.submit(request("local-ollama", "llama3.1")));

        assertThat(response.rawText()).contains("\"provider\":\"ollama\"");
        assertThat(response.metadata().inputTokens()).contains(3);
        assertThat(response.metadata().outputTokens()).contains(5);
    }

    @Test
    void rejectsProviderTypesWithoutExecutableAdapter() {
        var provider = publishedProvider(
                "anthropic-main",
                AiProviderType.ANTHROPIC,
                "https://api.anthropic.com",
                "claude-test");
        var gateway = gateway(provider, "anthropic-main");

        assertThatThrownBy(() -> await(gateway.submit(request("anthropic-main", "claude-test"))))
                .isInstanceOf(GatewayException.class)
                .hasMessageContaining("not executable yet");
    }

    @Test
    void fakeProviderReturnsHarnessJsonWithoutExternalCredential() throws Exception {
        var repository = mock(AiProviderCredentialRepository.class);
        var callLogService = mock(AiModelCallLogService.class);
        var properties = new AiCredentialProperties();
        properties.setMasterKey("test-master-key");
        var fakeProperties = new AiFakeProviderProperties();
        fakeProperties.setEnabled(true);
        var gateway = new ArchDoxProviderAiModelGateway(
                repository,
                new AiCredentialCipher(properties),
                callLogService,
                new ObjectMapper(),
                fakeProperties,
                new AiFakeResponseFactory(),
                new AiSpringAiAdapterProperties(),
                Optional.empty());

        var response = await(gateway.submit(request("fake-ops", "fake-model", "archdox-ops-diagnosis")));

        assertThat(response.rawText()).contains("\"status\": \"NEEDS_ATTENTION\"");
        assertThat(response.rawText()).contains("FAKE_OPS_DIAGNOSIS");
        assertThat(response.metadata().providerTrace()).containsEntry("fake", "true");
        verify(callLogService).recordSuccess(
                any(),
                isNull(),
                any(AiModelRequest.class),
                any(AiModelResponse.class),
                any(),
                any());
    }

    @Test
    void springAiAdapterDelegatesSpringAiPrefixedProviderToFlowerSpringAiGateway() throws Exception {
        var repository = mock(AiProviderCredentialRepository.class);
        var callLogService = mock(AiModelCallLogService.class);
        var properties = new AiCredentialProperties();
        properties.setMasterKey("test-master-key");
        var springAiProperties = new AiSpringAiAdapterProperties();
        springAiProperties.setEnabled(true);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var chatModel = new CapturingChatModel("""
                    {"status":"PASS","summary":"spring ai ok","confidence":"HIGH","issues":[]}
                    """);
            var gateway = new ArchDoxProviderAiModelGateway(
                    repository,
                    new AiCredentialCipher(properties),
                    callLogService,
                    new ObjectMapper(),
                    new AiFakeProviderProperties(),
                    new AiFakeResponseFactory(),
                    springAiProperties,
                    Optional.of(SpringAiModelGateway.fixed(ChatClient.create(chatModel), executor)));

            var response = await(gateway.submit(request("spring-ai-openai", "gpt-test")));

            assertThat(response.rawText()).contains("spring ai ok");
            assertThat(response.metadata().providerTrace()).containsEntry("springAi.model", "spring-ai-test-model");
            assertThat(chatModel.lastPrompt().getOptions().getModel()).isEqualTo("gpt-test");
            verify(callLogService).recordSuccess(
                    any(),
                    isNull(),
                    any(AiModelRequest.class),
                    any(AiModelResponse.class),
                    any(),
                    any());
        } finally {
            executor.shutdownNow();
        }
    }

    private ArchDoxProviderAiModelGateway gateway(AiProviderCredential provider, String providerCode) {
        var repository = mock(AiProviderCredentialRepository.class);
        when(repository.findByProviderCode(providerCode)).thenReturn(Optional.of(provider));
        var properties = new AiCredentialProperties();
        properties.setMasterKey("test-master-key");
        return new ArchDoxProviderAiModelGateway(
                repository,
                new AiCredentialCipher(properties),
                mock(AiModelCallLogService.class),
                new ObjectMapper(),
                new AiFakeProviderProperties(),
                new AiFakeResponseFactory(),
                new AiSpringAiAdapterProperties(),
                Optional.empty());
    }

    private AiProviderCredential publishedProvider(
            String providerCode,
            AiProviderType providerType,
            String baseUrl,
            String defaultModel
    ) {
        var now = OffsetDateTime.now();
        var provider = new AiProviderCredential(
                providerCode,
                providerCode,
                providerType,
                baseUrl,
                defaultModel,
                null,
                null,
                1L,
                now);
        provider.publish(now);
        return provider;
    }

    private AiModelRequest request(String providerCode, String model) {
        return request(providerCode, model, "test-prompt");
    }

    private AiModelRequest request(String providerCode, String model, String promptId) {
        return new AiModelRequest(
                new ModelId(providerCode, model),
                new RenderedPrompt(
                        List.of(
                                new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, "Return JSON only."),
                                new RenderedPrompt.Message(RenderedPrompt.Role.USER, "Review this document.")),
                        new PromptVersion(promptId, "v1")),
                ProviderOptions.empty(),
                Duration.ofSeconds(5));
    }

    private AiModelRequest connectionTestRequest(String providerCode, String model) {
        return new AiModelRequest(
                new ModelId(providerCode, model),
                new RenderedPrompt(
                        List.of(
                                new RenderedPrompt.Message(RenderedPrompt.Role.SYSTEM, "Return JSON only."),
                                new RenderedPrompt.Message(RenderedPrompt.Role.USER, "Return {\"status\":\"ok\"}.")),
                        new PromptVersion("archdox-provider-connection-test", "v1")),
                ProviderOptions.of(Map.of(AiModelCallMetadata.PROVIDER_CONNECTION_TEST, true)),
                Duration.ofSeconds(5));
    }

    private AiModelResponse await(AiModelCall call) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            var status = call.poll();
            if (status == AiModelCallStatus.READY) {
                return call.result();
            }
            if (status == AiModelCallStatus.FAILED) {
                var error = call.error();
                if (error instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new AssertionError(error);
            }
            Thread.sleep(10);
        }
        throw new AssertionError("AI model call did not complete in time");
    }

    private void startServer(
            String path,
            String responseJson,
            AtomicReference<String> capturedRequestBody
    ) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, exchange -> {
            capturedRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    private static final class CapturingChatModel implements ChatModel {
        private final String text;
        private final AtomicReference<Prompt> lastPrompt = new AtomicReference<>();

        private CapturingChatModel(String text) {
            this.text = text;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            lastPrompt.set(prompt);
            return response(text);
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(call(prompt));
        }

        private Prompt lastPrompt() {
            return lastPrompt.get();
        }
    }

    private static ChatResponse response(String text) {
        var generation = new Generation(
                new AssistantMessage(text),
                ChatGenerationMetadata.builder().finishReason("stop").build());
        return new ChatResponse(
                List.of(generation),
                ChatResponseMetadata.builder()
                        .id("spring-ai-response-1")
                        .model("spring-ai-test-model")
                        .usage(new TestUsage())
                        .build());
    }

    private static final class TestUsage implements Usage {
        @Override
        public Integer getPromptTokens() {
            return 13;
        }

        @Override
        public Integer getCompletionTokens() {
            return 8;
        }

        @Override
        public Object getNativeUsage() {
            return "test";
        }
    }
}
