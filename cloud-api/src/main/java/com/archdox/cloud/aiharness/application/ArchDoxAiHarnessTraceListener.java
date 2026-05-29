package com.archdox.cloud.aiharness.application;

import com.archdox.cloud.aipolicy.application.AiModelCallMetadata;
import io.github.parkkevinsb.flower.ai.harness.finding.AiFinding;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import io.github.parkkevinsb.flower.ai.harness.run.AiHarnessRunContext;
import io.github.parkkevinsb.flower.ai.harness.spi.TraceListener;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationError;
import io.github.parkkevinsb.flower.ai.harness.validate.ValidationResult;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ArchDoxAiHarnessTraceListener implements TraceListener {
    private final AiHarnessTraceEventService service;

    public ArchDoxAiHarnessTraceListener(AiHarnessTraceEventService service) {
        this.service = service;
    }

    @Override
    public void onRunStarted(AiHarnessRunContext ctx) {
        record("RUN_STARTED", ctx, null, null, null, null, null, null, null, null,
                "AI harness run started.",
                Map.of("startedAt", ctx.startedAt().toString()));
    }

    @Override
    public void onRequestSubmitted(AiHarnessRunContext ctx, AiModelRequest request, String callId) {
        record("REQUEST_SUBMITTED", ctx, officeId(request), request.modelId().asString(), callId, null, null, null, null, null,
                "AI model request submitted.",
                requestAttributes(request));
    }

    @Override
    public void onResponseReceived(AiHarnessRunContext ctx, AiModelResponse response) {
        record("RESPONSE_RECEIVED", ctx, officeId(ctx.currentRequest()), response.modelId().asString(), currentCallId(ctx),
                response.metadata().inputTokens().orElse(null),
                response.metadata().outputTokens().orElse(null),
                response.metadata().latency().map(Duration::toMillis).orElse(null),
                null,
                null,
                "AI model response received.",
                responseAttributes(response));
    }

    @Override
    public void onCallFailed(AiHarnessRunContext ctx, Throwable error) {
        record("CALL_FAILED", ctx, officeId(ctx.currentRequest()), modelId(ctx.currentRequest()), currentCallId(ctx),
                null, null, null, null, errorType(error),
                "AI model call failed.",
                attributes("errorMessage", bounded(error == null ? "" : error.getMessage(), 800)));
    }

    @Override
    public void onValidationCompleted(AiHarnessRunContext ctx, ValidationResult<?> result) {
        var attributes = new LinkedHashMap<String, Object>();
        var valid = result != null && result.isValid();
        var errorCount = 0;
        if (result instanceof ValidationResult.Invalid<?> invalid) {
            errorCount = invalid.errors().size();
            attributes.put("errors", invalid.errors().stream()
                    .limit(5)
                    .map(this::validationError)
                    .toList());
        }
        record("VALIDATION_COMPLETED", ctx, officeId(ctx.currentRequest()), modelId(ctx.currentRequest()), currentCallId(ctx),
                null, null, null, null, null,
                valid ? "AI response schema validation passed." : "AI response schema validation failed.",
                attributes,
                valid,
                errorCount);
    }

    @Override
    public void onRefineTriggered(AiHarnessRunContext ctx, AiModelRequest nextRequest) {
        record("REFINE_TRIGGERED", ctx, officeId(nextRequest), modelId(nextRequest), null, null, null, null, null, null,
                "AI harness triggered a refine attempt.",
                requestAttributes(nextRequest));
    }

    @Override
    public void onRunCompleted(AiHarnessRunContext ctx, List<AiFinding> findings) {
        var safeFindings = findings == null ? List.<AiFinding>of() : findings;
        record("RUN_COMPLETED", ctx, officeId(ctx.currentRequest()), modelId(ctx.currentRequest()), currentCallId(ctx),
                null, null, null, safeFindings.size(), null,
                "AI harness run completed.",
                Map.of("severityCounts", severityCounts(safeFindings)));
    }

    @Override
    public void onRunCancelled(AiHarnessRunContext ctx, String reason) {
        record("RUN_CANCELLED", ctx, officeId(ctx.currentRequest()), modelId(ctx.currentRequest()), currentCallId(ctx),
                null, null, null, null, null,
                "AI harness run cancelled.",
                attributes("reason", bounded(reason, 800)));
    }

    @Override
    public void onRunFailed(AiHarnessRunContext ctx, String reason) {
        record("RUN_FAILED", ctx, officeId(ctx.currentRequest()), modelId(ctx.currentRequest()), currentCallId(ctx),
                null, null, null, null, "HARNESS_RUN_FAILED",
                "AI harness run failed.",
                attributes("reason", bounded(reason, 800)));
    }

    private void record(
            String eventType,
            AiHarnessRunContext ctx,
            Long officeId,
            String modelId,
            String callId,
            Integer inputTokens,
            Integer outputTokens,
            Long latencyMs,
            Integer findingCount,
            String errorType,
            String message,
            Map<String, Object> attributes
    ) {
        record(eventType, ctx, officeId, modelId, callId, inputTokens, outputTokens, latencyMs, findingCount, errorType, message, attributes, null, null);
    }

    private void record(
            String eventType,
            AiHarnessRunContext ctx,
            Long officeId,
            String modelId,
            String callId,
            Integer inputTokens,
            Integer outputTokens,
            Long latencyMs,
            Integer findingCount,
            String errorType,
            String message,
            Map<String, Object> attributes,
            Boolean validationValid,
            Integer validationErrorCount
    ) {
        if (ctx == null) {
            return;
        }
        service.record(new AiHarnessTraceEventCommand(
                officeId,
                ctx.runId().value(),
                ctx.harnessId(),
                eventType,
                ctx.status().name(),
                ctx.attempt(),
                modelId,
                callId,
                ctx.promptVersion().id(),
                ctx.promptVersion().version(),
                inputTokens,
                outputTokens,
                latencyMs,
                findingCount,
                validationValid,
                validationErrorCount,
                errorType,
                message,
                attributes));
    }

    private Map<String, Object> requestAttributes(AiModelRequest request) {
        if (request == null) {
            return Map.of();
        }
        var attributes = new LinkedHashMap<String, Object>();
        attributes.put("timeoutMs", request.timeout().toMillis());
        putOption(attributes, request, AiModelCallMetadata.FEATURE);
        putOption(attributes, request, AiModelCallMetadata.WORKFLOW_TYPE);
        putOption(attributes, request, AiModelCallMetadata.WORKFLOW_KEY);
        putOption(attributes, request, AiModelCallMetadata.RESOURCE_TYPE);
        putOption(attributes, request, AiModelCallMetadata.RESOURCE_ID);
        return attributes;
    }

    private Map<String, Object> responseAttributes(AiModelResponse response) {
        var attributes = new LinkedHashMap<String, Object>();
        response.metadata().finishReason().ifPresent(value -> attributes.put("finishReason", value));
        if (!response.metadata().providerTrace().isEmpty()) {
            attributes.put("providerTrace", response.metadata().providerTrace());
        }
        return attributes;
    }

    private void putOption(Map<String, Object> attributes, AiModelRequest request, String key) {
        request.options().get(key).ifPresent(value -> attributes.put(key, value));
    }

    private Long officeId(AiModelRequest request) {
        if (request == null) {
            return null;
        }
        return request.options().get(AiModelCallMetadata.OFFICE_ID)
                .map(this::asLong)
                .orElse(null);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Long.valueOf(stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String modelId(AiModelRequest request) {
        return request == null ? null : request.modelId().asString();
    }

    private String currentCallId(AiHarnessRunContext ctx) {
        return ctx.currentCall().map(call -> bounded(call.callId(), 160)).orElse(null);
    }

    private String errorType(Throwable error) {
        return error == null ? null : bounded(error.getClass().getSimpleName(), 160);
    }

    private Map<String, Object> validationError(ValidationError error) {
        var value = new LinkedHashMap<String, Object>();
        value.put("path", error.path());
        value.put("code", error.code());
        value.put("message", bounded(error.message(), 400));
        return value;
    }

    private Map<String, Long> severityCounts(List<AiFinding> findings) {
        return findings.stream()
                .collect(Collectors.groupingBy(finding -> finding.severity().name(), LinkedHashMap::new, Collectors.counting()));
    }

    private Map<String, Object> attributes(String key, Object value) {
        if (value == null) {
            return Map.of();
        }
        return Map.of(key, value);
    }

    private String bounded(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
