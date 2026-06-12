package com.archdox.cloud.aipolicy.application;

import com.archdox.cloud.aipolicy.domain.AiProviderCredential;
import com.archdox.cloud.aipolicy.dto.AiProviderConnectionTestResponse;
import com.archdox.cloud.aipolicy.infra.AiProviderCredentialRepository;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import io.github.parkkevinsb.flower.ai.harness.gateway.AiModelGateway;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCall;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCallStatus;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.model.ModelId;
import io.github.parkkevinsb.flower.ai.harness.prompt.PromptVersion;
import io.github.parkkevinsb.flower.ai.harness.prompt.RenderedPrompt;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiProviderConnectionTestService {
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration TEST_POLL_INTERVAL = Duration.ofMillis(100);

    private final PlatformAdminService platformAdminService;
    private final AiProviderCredentialRepository providerRepository;
    private final AiModelGateway aiModelGateway;

    public AiProviderConnectionTestService(
            PlatformAdminService platformAdminService,
            AiProviderCredentialRepository providerRepository,
            AiModelGateway aiModelGateway
    ) {
        this.platformAdminService = platformAdminService;
        this.providerRepository = providerRepository;
        this.aiModelGateway = aiModelGateway;
    }

    @Transactional(readOnly = true)
    public AiProviderConnectionTestResponse testProvider(UserPrincipal principal, Long providerId) {
        platformAdminService.requirePlatformAdmin(principal);
        var provider = requireProvider(providerId);
        var modelName = requiredModel(provider);
        var testedAt = OffsetDateTime.now();
        var startedAt = System.nanoTime();
        var request = request(provider, modelName, principal.userId());
        try {
            var response = awaitAsync(aiModelGateway.submit(request), TEST_TIMEOUT.plusSeconds(2)).join();
            var latencyMs = response.metadata().latency()
                    .map(Duration::toMillis)
                    .orElse(Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
            return new AiProviderConnectionTestResponse(
                    provider.id(),
                    provider.providerCode(),
                    provider.providerType().name(),
                    modelName,
                    true,
                    "SUCCEEDED",
                    "AI provider connection test succeeded.",
                    latencyMs,
                    response.metadata().finishReason().orElse(null),
                    preview(response.rawText()),
                    testedAt);
        } catch (RuntimeException ex) {
            return new AiProviderConnectionTestResponse(
                    provider.id(),
                    provider.providerCode(),
                    provider.providerType().name(),
                    modelName,
                    false,
                    "FAILED",
                    friendlyMessage(ex),
                    Duration.ofNanos(System.nanoTime() - startedAt).toMillis(),
                    null,
                    null,
                    testedAt);
        }
    }

    private AiProviderCredential requireProvider(Long providerId) {
        if (providerId == null) {
            throw new BadRequestException("providerId is required");
        }
        return providerRepository.findById(providerId)
                .orElseThrow(() -> new NotFoundException("AI provider credential not found"));
    }

    private String requiredModel(AiProviderCredential provider) {
        var model = provider.defaultModel();
        if (model == null || model.isBlank()) {
            throw new BadRequestException(
                    "AI_PROVIDER_MODEL_REQUIRED",
                    "error.aiProvider.modelRequired",
                    "AI provider default model is required before connection test");
        }
        return model.trim();
    }

    private AiModelRequest request(AiProviderCredential provider, String modelName, Long userId) {
        return new AiModelRequest(
                new ModelId(provider.providerCode(), modelName),
                new RenderedPrompt(
                        List.of(
                                new RenderedPrompt.Message(
                                        RenderedPrompt.Role.SYSTEM,
                                        "You are a connectivity test. Reply with compact JSON only."),
                                new RenderedPrompt.Message(
                                        RenderedPrompt.Role.USER,
                                        "Return exactly {\"status\":\"ok\"}.")),
                        new PromptVersion("archdox-provider-connection-test", "v1")),
                AiModelCallMetadata.options(
                        null,
                        userId,
                        "PROVIDER_CONNECTION_TEST",
                        "ai-provider-connection-test",
                        "ai-provider:" + provider.id(),
                        "AI_PROVIDER_CREDENTIAL",
                        provider.id(),
                        Map.of(AiModelCallMetadata.PROVIDER_CONNECTION_TEST, true),
                        32),
                TEST_TIMEOUT);
    }

    private CompletableFuture<AiModelResponse> awaitAsync(
            AiModelCall call,
            Duration timeout
    ) {
        var result = new CompletableFuture<AiModelResponse>();
        poll(call, result, System.nanoTime() + timeout.toNanos());
        return result;
    }

    private void poll(
            AiModelCall call,
            CompletableFuture<AiModelResponse> result,
            long deadlineNanos
    ) {
        if (result.isDone()) {
            return;
        }
        try {
            var status = call.poll();
            if (status == AiModelCallStatus.READY) {
                result.complete(call.result());
                return;
            }
            if (status == AiModelCallStatus.FAILED) {
                var error = call.error();
                result.completeExceptionally(error instanceof RuntimeException runtimeException
                        ? runtimeException
                        : new IllegalStateException("AI provider connection test failed", error));
                return;
            }
            if (status == AiModelCallStatus.CANCELLED) {
                result.completeExceptionally(new IllegalStateException("AI provider connection test was cancelled"));
                return;
            }
            if (System.nanoTime() >= deadlineNanos) {
                call.cancel();
                result.completeExceptionally(new IllegalStateException("AI provider connection test timed out"));
                return;
            }
            CompletableFuture.runAsync(
                    () -> poll(call, result, deadlineNanos),
                    CompletableFuture.delayedExecutor(TEST_POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS))
                    .exceptionally(error -> {
                        result.completeExceptionally(error);
                        return null;
                    });
        } catch (RejectedExecutionException ex) {
            result.completeExceptionally(ex);
        } catch (RuntimeException ex) {
            result.completeExceptionally(ex);
        }
    }

    private String preview(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        var normalized = rawText.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300);
    }

    private String friendlyMessage(RuntimeException ex) {
        var message = rootMessage(ex);
        if (message == null || message.isBlank()) {
            return "AI provider connection test failed.";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        String message = null;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        return message;
    }
}
