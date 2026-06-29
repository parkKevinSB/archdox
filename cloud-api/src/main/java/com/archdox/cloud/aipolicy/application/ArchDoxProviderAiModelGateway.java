package com.archdox.cloud.aipolicy.application;

import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.domain.AiProviderCredentialStatus;
import com.archdox.cloud.aipolicy.domain.AiProviderType;
import com.archdox.cloud.aipolicy.infra.AiProviderCredentialRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.gateway.GatewayException;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCall;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCallStatus;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import io.github.parkkevinsb.flower.ai.harness.provider.openaicompatible.OpenAiCompatibleGatewayConfig;
import io.github.parkkevinsb.flower.ai.harness.provider.openaicompatible.OpenAiCompatibleModelGateway;
import io.github.parkkevinsb.flower.ai.harness.provider.openaicompatible.OpenAiCompatibleOptions;
import io.github.parkkevinsb.flower.ai.harness.springai.SpringAiModelGateway;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class ArchDoxProviderAiModelGateway implements AiModelGateway {
    private final AiProviderCredentialRepository providerRepository;
    private final AiCredentialCipher credentialCipher;
    private final AiModelCallLogService callLogService;
    private final AiFakeProviderProperties fakeProviderProperties;
    private final AiFakeResponseFactory fakeResponseFactory;
    private final AiSpringAiAdapterProperties springAiAdapterProperties;
    private final AiObservationBufferService observationBufferService;
    private final Optional<SpringAiModelGateway> springAiModelGateway;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Executor aiModelGatewayExecutor;

    @Autowired
    public ArchDoxProviderAiModelGateway(
            AiProviderCredentialRepository providerRepository,
            AiCredentialCipher credentialCipher,
            AiModelCallLogService callLogService,
            ObjectMapper objectMapper,
            AiFakeProviderProperties fakeProviderProperties,
            AiFakeResponseFactory fakeResponseFactory,
            AiSpringAiAdapterProperties springAiAdapterProperties,
            AiObservationBufferService observationBufferService,
            Optional<SpringAiModelGateway> springAiModelGateway,
            @Qualifier(AiModelGatewayExecutionConfiguration.AI_MODEL_GATEWAY_EXECUTOR) Executor aiModelGatewayExecutor
    ) {
        this.providerRepository = providerRepository;
        this.credentialCipher = credentialCipher;
        this.callLogService = callLogService;
        this.fakeProviderProperties = fakeProviderProperties;
        this.fakeResponseFactory = fakeResponseFactory;
        this.springAiAdapterProperties = springAiAdapterProperties;
        this.observationBufferService = observationBufferService;
        this.springAiModelGateway = springAiModelGateway == null ? Optional.empty() : springAiModelGateway;
        this.objectMapper = objectMapper;
        this.aiModelGatewayExecutor = aiModelGatewayExecutor == null
                ? ForkJoinPool.commonPool()
                : aiModelGatewayExecutor;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public ArchDoxProviderAiModelGateway(
            AiProviderCredentialRepository providerRepository,
            AiCredentialCipher credentialCipher,
            AiModelCallLogService callLogService,
            ObjectMapper objectMapper,
            AiFakeProviderProperties fakeProviderProperties,
            AiFakeResponseFactory fakeResponseFactory,
            AiSpringAiAdapterProperties springAiAdapterProperties,
            Optional<SpringAiModelGateway> springAiModelGateway
    ) {
        this(
                providerRepository,
                credentialCipher,
                callLogService,
                objectMapper,
                fakeProviderProperties,
                fakeResponseFactory,
                springAiAdapterProperties,
                AiObservationBufferService.disabled(),
                springAiModelGateway,
                ForkJoinPool.commonPool());
    }

    ArchDoxProviderAiModelGateway(
            AiProviderCredentialRepository providerRepository,
            AiCredentialCipher credentialCipher,
            AiModelCallLogService callLogService,
            ObjectMapper objectMapper,
            AiFakeProviderProperties fakeProviderProperties,
            AiFakeResponseFactory fakeResponseFactory,
            AiSpringAiAdapterProperties springAiAdapterProperties,
            Optional<SpringAiModelGateway> springAiModelGateway,
            Executor aiModelGatewayExecutor
    ) {
        this(
                providerRepository,
                credentialCipher,
                callLogService,
                objectMapper,
                fakeProviderProperties,
                fakeResponseFactory,
                springAiAdapterProperties,
                AiObservationBufferService.disabled(),
                springAiModelGateway,
                aiModelGatewayExecutor);
    }

    @Override
    public AiModelCall submit(AiModelRequest request) {
        var callId = "archdox-ai-" + UUID.randomUUID();
        observationBufferService.observeSubmitted(callId, request, OffsetDateTime.now());
        var cancelProviderRef = new AtomicReference<Runnable>(() -> {
        });
        CompletableFuture<AiModelResponse> future;
        try {
            future = CompletableFuture.supplyAsync(
                            () -> callProviderAsync(callId, request, cancelProviderRef),
                            aiModelGatewayExecutor)
                    .thenCompose(Function.identity());
        } catch (RejectedExecutionException ex) {
            var failure = new GatewayException(
                    "AI_MODEL_GATEWAY_QUEUE_FULL: AI model gateway execution queue is full", ex);
            recordFailureSafely(callId, null, request, failure, OffsetDateTime.now());
            observationBufferService.observeFailure(callId, request, failure, OffsetDateTime.now());
            future = CompletableFuture.failedFuture(failure);
        }
        var timedFuture = future.orTimeout(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
        timedFuture.whenComplete((response, error) -> {
            if (isTimeout(error)) {
                cancelProviderRef.get().run();
            }
        });
        return new ArchDoxAiModelCall(callId, timedFuture, () -> cancelProviderRef.get().run());
    }

    private CompletableFuture<AiModelResponse> callProviderAsync(
            String callId,
            AiModelRequest request,
            AtomicReference<Runnable> cancelProviderRef
    ) {
        var requestedAt = OffsetDateTime.now();
        AiProviderCredential provider = null;
        try {
            if (fakeProviderProperties.supports(request.modelId().provider())) {
                provider = providerRepository.findByProviderCode(request.modelId().provider()).orElse(null);
                var response = fakeResponseFactory.create(request, fakeProviderProperties.safeLatencyMs());
                recordSuccessSafely(callId, provider, request, response, requestedAt);
                observationBufferService.observeSuccess(callId, request, response, OffsetDateTime.now());
                return CompletableFuture.completedFuture(response);
            }
            if (springAiAdapterProperties.supports(request.modelId().provider())) {
                provider = providerRepository.findByProviderCode(request.modelId().provider()).orElse(null);
                return callSpringAiAdapterAsync(callId, provider, request, requestedAt, cancelProviderRef);
            }
            provider = providerRepository.findByProviderCode(request.modelId().provider())
                    .orElseThrow(() -> new GatewayException("No AI provider credential for provider code: "
                            + request.modelId().provider()));
            if (provider.status() != AiProviderCredentialStatus.ACTIVE && !providerConnectionTest(request)) {
                throw new GatewayException("AI provider credential is not active: " + provider.providerCode());
            }
            return switch (provider.providerType()) {
                case OPENAI, CUSTOM_HTTP -> callOpenAiCompatibleAdapterAsync(
                        callId,
                        provider,
                        request,
                        requestedAt,
                        cancelProviderRef);
                case OLLAMA -> callOllamaAsync(
                        callId,
                        provider,
                        request,
                        requestedAt,
                        cancelProviderRef);
                case GEMINI, ANTHROPIC -> throw new GatewayException(
                        "AI provider type is registered but not executable yet: " + provider.providerType().name()
                                + ". Use CUSTOM_HTTP for OpenAI-compatible gateways.");
            };
        } catch (RuntimeException ex) {
            recordFailureSafely(callId, provider, request, ex, requestedAt);
            observationBufferService.observeFailure(callId, request, ex, OffsetDateTime.now());
            return CompletableFuture.failedFuture(ex);
        }
    }

    private CompletableFuture<AiModelResponse> callSpringAiAdapterAsync(
            String callId,
            AiProviderCredential provider,
            AiModelRequest request,
            OffsetDateTime requestedAt,
            AtomicReference<Runnable> cancelProviderRef
    ) {
        try {
            var gateway = springAiModelGateway.orElseThrow(() -> new GatewayException(
                    "Spring AI adapter is enabled but SpringAiModelGateway is not configured"));
            var call = gateway.submit(withSpringAiModelOption(request));
            var future = new CompletableFuture<AiModelResponse>();
            cancelProviderRef.set(() -> {
                call.cancel();
                future.cancel(true);
            });
            pollSpringAiAdapter(call, future, System.nanoTime() + request.timeout().toNanos());
            return future.handle((response, error) -> {
                if (error == null) {
                    recordSuccessSafely(callId, provider, request, response, requestedAt);
                    observationBufferService.observeSuccess(callId, request, response, OffsetDateTime.now());
                    return response;
                }
                var failure = providerFailure("Spring AI provider call failed", error);
                recordFailureSafely(callId, provider, request, failure, requestedAt);
                observationBufferService.observeFailure(callId, request, failure, OffsetDateTime.now());
                throw failure;
            });
        } catch (RejectedExecutionException ex) {
            var failure = new GatewayException(
                    "AI_MODEL_GATEWAY_QUEUE_FULL: AI model gateway execution queue is full", ex);
            recordFailureSafely(callId, provider, request, failure, requestedAt);
            observationBufferService.observeFailure(callId, request, failure, OffsetDateTime.now());
            return CompletableFuture.failedFuture(failure);
        }
    }

    private void pollSpringAiAdapter(
            AiModelCall call,
            CompletableFuture<AiModelResponse> resultFuture,
            long deadlineNanos
    ) {
        pollProviderAdapter("Spring AI", call, resultFuture, deadlineNanos);
    }

    private void pollProviderAdapter(
            String adapterName,
            AiModelCall call,
            CompletableFuture<AiModelResponse> resultFuture,
            long deadlineNanos
    ) {
        if (resultFuture.isDone()) {
            return;
        }
        try {
            var status = call.poll();
            if (status == AiModelCallStatus.READY) {
                resultFuture.complete(call.result());
                return;
            }
            if (status == AiModelCallStatus.FAILED) {
                resultFuture.completeExceptionally(new GatewayException(
                        adapterName + " model call failed: " + call.callId(),
                        call.error()));
                return;
            }
            if (status == AiModelCallStatus.CANCELLED) {
                resultFuture.completeExceptionally(new GatewayException(
                        adapterName + " model call was cancelled: " + call.callId()));
                return;
            }
            if (System.nanoTime() >= deadlineNanos) {
                call.cancel();
                resultFuture.completeExceptionally(new GatewayException(
                        adapterName + " model call timed out: " + call.callId()));
                return;
            }
            scheduleProviderAdapterPoll(adapterName, call, resultFuture, deadlineNanos);
        } catch (RuntimeException ex) {
            resultFuture.completeExceptionally(ex);
        }
    }

    private void scheduleProviderAdapterPoll(
            String adapterName,
            AiModelCall call,
            CompletableFuture<AiModelResponse> resultFuture,
            long deadlineNanos
    ) {
        try {
            CompletableFuture.runAsync(
                    () -> pollProviderAdapter(adapterName, call, resultFuture, deadlineNanos),
                    CompletableFuture.delayedExecutor(
                            springAiAdapterProperties.safePollIntervalMs(),
                            TimeUnit.MILLISECONDS,
                            aiModelGatewayExecutor))
                    .exceptionally(error -> {
                        if (!resultFuture.isDone()) {
                            resultFuture.completeExceptionally(providerFailure(
                                    adapterName + " model polling failed: " + call.callId(),
                                    error));
                        }
                        return null;
                    });
        } catch (RejectedExecutionException ex) {
            resultFuture.completeExceptionally(new GatewayException(
                    "AI_MODEL_GATEWAY_QUEUE_FULL: AI model gateway execution queue is full", ex));
        }
    }

    private AiModelRequest withSpringAiModelOption(AiModelRequest request) {
        if (request.options().get("model").isPresent()) {
            return request;
        }
        return request.withOptions(request.options().with("model", request.modelId().name()));
    }

    private CompletableFuture<AiModelResponse> callOpenAiCompatibleAdapterAsync(
            String callId,
            AiProviderCredential provider,
            AiModelRequest request,
            OffsetDateTime requestedAt,
            AtomicReference<Runnable> cancelProviderRef
    ) {
        try {
            var adapter = new OpenAiCompatibleModelGateway(
                    OpenAiCompatibleGatewayConfig.builder(openAiCompatibleBaseUri(provider))
                            .apiKey(openAiApiKey(provider))
                            .build(),
                    httpClient,
                    objectMapper);
            var adapterRequest = withOpenAiCompatibleDefaults(request);
            var call = adapter.submit(adapterRequest);
            var future = new CompletableFuture<AiModelResponse>();
            cancelProviderRef.set(() -> {
                call.cancel();
                future.cancel(true);
            });
            pollProviderAdapter("OpenAI-compatible", call, future, System.nanoTime() + request.timeout().toNanos());
            return future.handle((response, error) -> {
                if (error == null) {
                    var enriched = withProviderTrace(provider, response);
                    recordSuccessSafely(callId, provider, request, enriched, requestedAt);
                    observationBufferService.observeSuccess(callId, request, enriched, OffsetDateTime.now());
                    return enriched;
                }
                var failure = providerFailure("AI provider call failed: " + provider.providerCode(), error);
                recordFailureSafely(callId, provider, request, failure, requestedAt);
                observationBufferService.observeFailure(callId, request, failure, OffsetDateTime.now());
                throw failure;
            });
        } catch (RuntimeException ex) {
            var failure = providerFailure("AI provider call failed: " + provider.providerCode(), ex);
            recordFailureSafely(callId, provider, request, failure, requestedAt);
            observationBufferService.observeFailure(callId, request, failure, OffsetDateTime.now());
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletableFuture<AiModelResponse> callOllamaAsync(
            String callId,
            AiProviderCredential provider,
            AiModelRequest request,
            OffsetDateTime requestedAt,
            AtomicReference<Runnable> cancelProviderRef
    ) {
        var startedAt = System.nanoTime();
        try {
            var body = new LinkedHashMap<String, Object>();
            body.put("model", request.modelId().name());
            body.put("messages", promptMessages(request.prompt()));
            body.put("stream", false);
            option(request, "maxTokens").ifPresent(value -> body.put("options", Map.of("num_predict", value)));

            var httpRequest = HttpRequest.newBuilder()
                    .uri(ollamaChatUri(provider))
                    .timeout(request.timeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            var httpFuture = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString());
            cancelProviderRef.set(() -> httpFuture.cancel(true));
            return httpFuture.handle((response, error) -> {
                if (error != null) {
                    var failure = providerFailure("Ollama AI provider call failed: " + provider.providerCode(), error);
                    recordFailureSafely(callId, provider, request, failure, requestedAt);
                    observationBufferService.observeFailure(callId, request, failure, OffsetDateTime.now());
                    throw failure;
                }
                try {
                    var aiResponse = parseOllamaResponse(provider, request, response, startedAt);
                    recordSuccessSafely(callId, provider, request, aiResponse, requestedAt);
                    observationBufferService.observeSuccess(callId, request, aiResponse, OffsetDateTime.now());
                    return aiResponse;
                } catch (RuntimeException ex) {
                    recordFailureSafely(callId, provider, request, ex, requestedAt);
                    observationBufferService.observeFailure(callId, request, ex, OffsetDateTime.now());
                    throw ex;
                }
            });
        } catch (GatewayException ex) {
            recordFailureSafely(callId, provider, request, ex, requestedAt);
            observationBufferService.observeFailure(callId, request, ex, OffsetDateTime.now());
            return CompletableFuture.failedFuture(ex);
        } catch (Exception ex) {
            var failure = new GatewayException("Ollama AI provider call failed: " + provider.providerCode(), ex);
            recordFailureSafely(callId, provider, request, failure, requestedAt);
            observationBufferService.observeFailure(callId, request, failure, OffsetDateTime.now());
            return CompletableFuture.failedFuture(failure);
        }
    }

    private AiModelResponse parseOllamaResponse(
            AiProviderCredential provider,
            AiModelRequest request,
            HttpResponse<String> response,
            long startedAt
    ) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GatewayException("Ollama returned HTTP " + response.statusCode());
        }
        try {
            var json = objectMapper.readTree(response.body());
            var text = json.path("message").path("content").asText("");
            return new AiModelResponse(
                    text,
                    request.modelId(),
                    new AiModelResponse.ResponseMetadata(
                            optionalInt(json.path("prompt_eval_count")),
                            optionalInt(json.path("eval_count")),
                            Optional.of(Duration.ofNanos(System.nanoTime() - startedAt)),
                            Optional.ofNullable(json.path("done_reason").asText(null)),
                            Map.of(
                                    "providerCode", provider.providerCode(),
                                    "providerType", provider.providerType().name())));
        } catch (Exception ex) {
            throw new GatewayException("Ollama AI provider response parsing failed: " + provider.providerCode(), ex);
        }
    }

    private URI openAiCompatibleBaseUri(AiProviderCredential provider) {
        var baseUrl = blankToDefault(provider.baseUrl(), "https://api.openai.com/v1");
        return URI.create(baseUrl);
    }

    private URI ollamaChatUri(AiProviderCredential provider) {
        var baseUrl = blankToDefault(provider.baseUrl(), "http://localhost:11434");
        var normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/api/chat")) {
            return URI.create(normalized);
        }
        return URI.create(normalized + "/api/chat");
    }

    private ArrayList<Map<String, String>> promptMessages(RenderedPrompt prompt) {
        var messages = new ArrayList<Map<String, String>>();
        for (var message : prompt.messages()) {
            messages.add(Map.of(
                    "role", role(message.role()),
                    "content", message.content()));
        }
        return messages;
    }

    private String role(RenderedPrompt.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    private Optional<Object> option(AiModelRequest request, String key) {
        return request.options().get(key);
    }

    private boolean providerConnectionTest(AiModelRequest request) {
        return request.options().get(AiModelCallMetadata.PROVIDER_CONNECTION_TEST)
                .map(value -> {
                    if (value instanceof Boolean booleanValue) {
                        return booleanValue;
                    }
                    return Boolean.parseBoolean(String.valueOf(value));
                })
                .orElse(false);
    }

    private String openAiApiKey(AiProviderCredential provider) {
        var apiKey = credentialCipher.decrypt(provider.encryptedApiKey());
        if (provider.providerType() == AiProviderType.OPENAI && (apiKey == null || apiKey.isBlank())) {
            throw new GatewayException("OpenAI provider requires an API key: " + provider.providerCode());
        }
        if (apiKey != null
                && !apiKey.isBlank()
                && provider.providerType() == AiProviderType.OPENAI
                && invalidOpenAiApiKey(apiKey)) {
            throw new GatewayException("OpenAI API key format is invalid for provider: " + provider.providerCode());
        }
        return apiKey;
    }

    private AiModelRequest withOpenAiCompatibleDefaults(AiModelRequest request) {
        if (request.options().get(OpenAiCompatibleOptions.TEMPERATURE).isPresent()) {
            return request;
        }
        return request.withOptions(request.options().with(OpenAiCompatibleOptions.TEMPERATURE, 0.1));
    }

    private AiModelResponse withProviderTrace(AiProviderCredential provider, AiModelResponse response) {
        var trace = new LinkedHashMap<String, String>();
        if (provider != null) {
            trace.put("providerCode", provider.providerCode());
            trace.put("providerType", provider.providerType().name());
        }
        trace.putAll(response.metadata().providerTrace());
        return new AiModelResponse(
                response.rawText(),
                response.modelId(),
                new AiModelResponse.ResponseMetadata(
                        response.metadata().inputTokens(),
                        response.metadata().outputTokens(),
                        response.metadata().latency(),
                        response.metadata().finishReason(),
                        Map.copyOf(trace)));
    }

    private boolean invalidOpenAiApiKey(String apiKey) {
        return apiKey.contains(" ")
                || apiKey.contains("\n")
                || apiKey.contains("\r")
                || !(apiKey.startsWith("sk-") || apiKey.startsWith("sk-proj-"));
    }

    private Optional<Integer> optionalInt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.canConvertToInt()) {
            return Optional.empty();
        }
        return Optional.of(node.asInt());
    }

    private RuntimeException providerFailure(String fallbackMessage, Throwable error) {
        var cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof GatewayException gatewayException) {
            return gatewayException;
        }
        if (cause instanceof RuntimeException runtimeException) {
            return new GatewayException(fallbackMessage, runtimeException);
        }
        return new GatewayException(fallbackMessage, cause);
    }

    private boolean isTimeout(Throwable error) {
        var cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof TimeoutException;
    }

    private void recordSuccessSafely(
            String callId,
            AiProviderCredential provider,
            AiModelRequest request,
            AiModelResponse response,
            OffsetDateTime requestedAt
    ) {
        try {
            callLogService.recordSuccess(callId, provider, request, response, requestedAt, OffsetDateTime.now());
        } catch (RuntimeException ignored) {
            // AI call logging must not change the model call result.
        }
    }

    private void recordFailureSafely(
            String callId,
            AiProviderCredential provider,
            AiModelRequest request,
            RuntimeException error,
            OffsetDateTime requestedAt
    ) {
        try {
            callLogService.recordFailure(callId, provider, request, error, requestedAt, OffsetDateTime.now());
        } catch (RuntimeException ignored) {
            // AI call logging must not hide the original gateway error.
        }
    }

    private String blankToDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}
