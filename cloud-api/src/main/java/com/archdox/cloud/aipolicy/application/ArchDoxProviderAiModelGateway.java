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
import java.util.concurrent.TimeUnit;
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
    private final Optional<SpringAiModelGateway> springAiModelGateway;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

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
        this.providerRepository = providerRepository;
        this.credentialCipher = credentialCipher;
        this.callLogService = callLogService;
        this.fakeProviderProperties = fakeProviderProperties;
        this.fakeResponseFactory = fakeResponseFactory;
        this.springAiAdapterProperties = springAiAdapterProperties;
        this.springAiModelGateway = springAiModelGateway == null ? Optional.empty() : springAiModelGateway;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public AiModelCall submit(AiModelRequest request) {
        var callId = "archdox-ai-" + UUID.randomUUID();
        var future = CompletableFuture.supplyAsync(() -> callProvider(callId, request));
        return new ArchDoxAiModelCall(callId, future.orTimeout(
                request.timeout().toMillis(),
                java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    private AiModelResponse callProvider(String callId, AiModelRequest request) {
        var requestedAt = OffsetDateTime.now();
        AiProviderCredential provider = null;
        try {
            if (fakeProviderProperties.supports(request.modelId().provider())) {
                provider = providerRepository.findByProviderCode(request.modelId().provider()).orElse(null);
                var response = fakeResponseFactory.create(request, fakeProviderProperties.safeLatencyMs());
                recordSuccessSafely(callId, provider, request, response, requestedAt);
                return response;
            }
            if (springAiAdapterProperties.supports(request.modelId().provider())) {
                provider = providerRepository.findByProviderCode(request.modelId().provider()).orElse(null);
                var response = callSpringAiAdapter(request);
                recordSuccessSafely(callId, provider, request, response, requestedAt);
                return response;
            }
            provider = providerRepository.findByProviderCode(request.modelId().provider())
                    .orElseThrow(() -> new GatewayException("No AI provider credential for provider code: "
                            + request.modelId().provider()));
            if (provider.status() != AiProviderCredentialStatus.ACTIVE) {
                throw new GatewayException("AI provider credential is not active: " + provider.providerCode());
            }
            var response = switch (provider.providerType()) {
                case OPENAI, CUSTOM_HTTP -> callOpenAiCompatible(provider, request);
                case OLLAMA -> callOllama(provider, request);
                case GEMINI, ANTHROPIC -> throw new GatewayException(
                        "AI provider type is registered but not executable yet: " + provider.providerType().name()
                                + ". Use CUSTOM_HTTP for OpenAI-compatible gateways.");
            };
            recordSuccessSafely(callId, provider, request, response, requestedAt);
            return response;
        } catch (RuntimeException ex) {
            recordFailureSafely(callId, provider, request, ex, requestedAt);
            throw ex;
        }
    }

    private AiModelResponse callSpringAiAdapter(AiModelRequest request) {
        var gateway = springAiModelGateway.orElseThrow(() -> new GatewayException(
                "Spring AI adapter is enabled but SpringAiModelGateway is not configured"));
        var call = gateway.submit(withSpringAiModelOption(request));
        var deadline = System.nanoTime() + request.timeout().toNanos();
        while (System.nanoTime() < deadline) {
            var status = call.poll();
            if (status == AiModelCallStatus.READY) {
                return call.result();
            }
            if (status == AiModelCallStatus.FAILED) {
                throw new GatewayException("Spring AI model call failed: " + call.callId(), call.error());
            }
            if (status == AiModelCallStatus.CANCELLED) {
                throw new GatewayException("Spring AI model call was cancelled: " + call.callId());
            }
            sleepSpringAiPollInterval();
        }
        call.cancel();
        throw new GatewayException("Spring AI model call timed out: " + call.callId());
    }

    private AiModelRequest withSpringAiModelOption(AiModelRequest request) {
        if (request.options().get("model").isPresent()) {
            return request;
        }
        return request.withOptions(request.options().with("model", request.modelId().name()));
    }

    private void sleepSpringAiPollInterval() {
        try {
            TimeUnit.MILLISECONDS.sleep(springAiAdapterProperties.safePollIntervalMs());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GatewayException("Interrupted while waiting for Spring AI model call", ex);
        }
    }

    private AiModelResponse callOpenAiCompatible(AiProviderCredential provider, AiModelRequest request) {
        var startedAt = System.nanoTime();
        try {
            var body = new LinkedHashMap<String, Object>();
            body.put("model", request.modelId().name());
            body.put("messages", promptMessages(request.prompt()));
            body.put("temperature", numberOption(request, "temperature").orElse(0.1));
            option(request, "maxTokens").ifPresent(value -> body.put("max_tokens", value));

            var builder = HttpRequest.newBuilder()
                    .uri(openAiChatCompletionsUri(provider))
                    .timeout(request.timeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            var apiKey = credentialCipher.decrypt(provider.encryptedApiKey());
            if (provider.providerType() == AiProviderType.OPENAI && (apiKey == null || apiKey.isBlank())) {
                throw new GatewayException("OpenAI provider requires an API key: " + provider.providerCode());
            }
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }

            var response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GatewayException("AI provider returned HTTP " + response.statusCode());
            }
            var json = objectMapper.readTree(response.body());
            var text = json.path("choices").path(0).path("message").path("content").asText("");
            var usage = json.path("usage");
            return new AiModelResponse(
                    text,
                    request.modelId(),
                    new AiModelResponse.ResponseMetadata(
                            optionalInt(usage.path("prompt_tokens")),
                            optionalInt(usage.path("completion_tokens")),
                            Optional.of(Duration.ofNanos(System.nanoTime() - startedAt)),
                            Optional.ofNullable(json.path("choices").path(0).path("finish_reason").asText(null)),
                            Map.of(
                                    "providerCode", provider.providerCode(),
                                    "providerType", provider.providerType().name(),
                                    "providerResponseId", json.path("id").asText(""))));
        } catch (GatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GatewayException("AI provider call failed: " + provider.providerCode(), ex);
        }
    }

    private AiModelResponse callOllama(AiProviderCredential provider, AiModelRequest request) {
        var startedAt = System.nanoTime();
        try {
            var body = new LinkedHashMap<String, Object>();
            body.put("model", request.modelId().name());
            body.put("messages", promptMessages(request.prompt()));
            body.put("stream", false);

            var httpRequest = HttpRequest.newBuilder()
                    .uri(ollamaChatUri(provider))
                    .timeout(request.timeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GatewayException("Ollama returned HTTP " + response.statusCode());
            }
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
        } catch (GatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GatewayException("Ollama AI provider call failed: " + provider.providerCode(), ex);
        }
    }

    private URI openAiChatCompletionsUri(AiProviderCredential provider) {
        var baseUrl = blankToDefault(provider.baseUrl(), "https://api.openai.com/v1");
        var normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/chat/completions")) {
            return URI.create(normalized);
        }
        return URI.create(normalized + "/chat/completions");
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

    private Optional<Number> numberOption(AiModelRequest request, String key) {
        return request.options().get(key)
                .filter(Number.class::isInstance)
                .map(Number.class::cast);
    }

    private Optional<Integer> optionalInt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.canConvertToInt()) {
            return Optional.empty();
        }
        return Optional.of(node.asInt());
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
