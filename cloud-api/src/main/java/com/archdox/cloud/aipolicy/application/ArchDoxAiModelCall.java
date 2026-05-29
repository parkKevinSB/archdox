package com.archdox.cloud.aipolicy.application;

import io.github.parkkevinsb.flower.ai.harness.model.AiModelCall;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelCallStatus;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

final class ArchDoxAiModelCall implements AiModelCall {
    private final String callId;
    private final CompletableFuture<AiModelResponse> future;

    ArchDoxAiModelCall(String callId, CompletableFuture<AiModelResponse> future) {
        this.callId = callId;
        this.future = future;
    }

    @Override
    public String callId() {
        return callId;
    }

    @Override
    public AiModelCallStatus poll() {
        if (future.isCancelled()) {
            return AiModelCallStatus.CANCELLED;
        }
        if (!future.isDone()) {
            return AiModelCallStatus.PENDING;
        }
        return error() == null ? AiModelCallStatus.READY : AiModelCallStatus.FAILED;
    }

    @Override
    public AiModelResponse result() {
        if (poll() != AiModelCallStatus.READY) {
            throw new IllegalStateException("AI model call is not ready: " + callId);
        }
        return future.join();
    }

    @Override
    public Throwable error() {
        if (!future.isDone() || future.isCancelled()) {
            return null;
        }
        try {
            future.join();
            return null;
        } catch (CompletionException ex) {
            return ex.getCause() == null ? ex : ex.getCause();
        } catch (CancellationException ex) {
            return ex;
        }
    }

    @Override
    public void cancel() {
        future.cancel(true);
    }
}
