package com.archdox.cloud.aipolicy.application;

import com.archdox.cloud.aipolicy.dto.AiObservationMessageResponse;
import com.archdox.cloud.aipolicy.dto.AiObservationModeResponse;
import com.archdox.cloud.aipolicy.dto.AiObservationResponse;
import com.archdox.cloud.aipolicy.dto.UpdateAiObservationModeRequest;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelRequest;
import io.github.parkkevinsb.flower.ai.harness.model.AiModelResponse;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AiObservationBufferService {
    private static final Set<String> SENSITIVE_OPTION_KEYS = Set.of(
            "apiKey",
            "apikey",
            "token",
            "secret",
            "authorization",
            "password");

    private final Object lock = new Object();
    private final AiObservationProperties properties;
    private final PlatformAdminService platformAdminService;
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private final ArrayDeque<String> newestFirst = new ArrayDeque<>();
    private boolean enabled;
    private OffsetDateTime modeUpdatedAt;

    public AiObservationBufferService(
            AiObservationProperties properties,
            PlatformAdminService platformAdminService
    ) {
        this.properties = properties;
        this.platformAdminService = platformAdminService;
        this.enabled = properties.isEnabled();
        this.modeUpdatedAt = OffsetDateTime.now();
    }

    public static AiObservationBufferService disabled() {
        var properties = new AiObservationProperties();
        properties.setEnabled(false);
        return new AiObservationBufferService(properties, null);
    }

    public void observeSubmitted(String callId, AiModelRequest request, OffsetDateTime observedAt) {
        synchronized (lock) {
            if (!enabled || blank(callId) || request == null) {
                return;
            }
            pruneLocked(observedAt);
            var promptMessages = promptMessages(request);
            var entry = Entry.submitted(
                    callId,
                    request.modelId().provider(),
                    request.modelId().asString(),
                    request.modelId().name(),
                    officeId(request),
                    optionText(request, AiModelCallMetadata.FEATURE),
                    optionText(request, AiModelCallMetadata.WORKFLOW_TYPE),
                    optionText(request, AiModelCallMetadata.WORKFLOW_KEY),
                    optionText(request, AiModelCallMetadata.RESOURCE_TYPE),
                    optionText(request, AiModelCallMetadata.RESOURCE_ID),
                    requestOptions(request),
                    promptMessages,
                    promptTruncated(promptMessages, request),
                    observedAt);
            putNewestLocked(entry);
            pruneLocked(observedAt);
        }
    }

    public void observeSuccess(String callId, AiModelRequest request, AiModelResponse response, OffsetDateTime observedAt) {
        synchronized (lock) {
            var entry = entryForUpdateLocked(callId, request, observedAt);
            if (entry == null) {
                return;
            }
            entry.status = "SUCCEEDED";
            entry.responseText = bounded(response == null ? null : response.rawText(), properties.safeMaxResponseChars());
            entry.responseTruncated = truncated(response == null ? null : response.rawText(), properties.safeMaxResponseChars());
            if (response != null) {
                entry.inputTokens = response.metadata().inputTokens().orElse(null);
                entry.outputTokens = response.metadata().outputTokens().orElse(null);
                entry.latencyMs = response.metadata().latency().map(java.time.Duration::toMillis).orElse(null);
                entry.finishReason = response.metadata().finishReason().orElse(null);
                entry.providerTrace = Map.copyOf(response.metadata().providerTrace());
            }
            entry.updatedAt = observedAt;
            pruneLocked(observedAt);
        }
    }

    public void observeFailure(String callId, AiModelRequest request, Throwable error, OffsetDateTime observedAt) {
        synchronized (lock) {
            var entry = entryForUpdateLocked(callId, request, observedAt);
            if (entry == null) {
                return;
            }
            entry.status = "FAILED";
            entry.errorType = error == null ? null : bounded(error.getClass().getSimpleName(), 160);
            entry.errorMessage = error == null ? null : bounded(error.getMessage(), properties.safeMaxResponseChars());
            entry.updatedAt = observedAt;
            pruneLocked(observedAt);
        }
    }

    public AiObservationModeResponse mode(UserPrincipal principal) {
        requirePlatformAdmin(principal);
        synchronized (lock) {
            pruneLocked(OffsetDateTime.now());
            return modeResponseLocked();
        }
    }

    public AiObservationModeResponse updateMode(UserPrincipal principal, UpdateAiObservationModeRequest request) {
        requirePlatformAdmin(principal);
        synchronized (lock) {
            this.enabled = request.enabled() != null ? request.enabled() : this.enabled;
            this.modeUpdatedAt = OffsetDateTime.now();
            if (Boolean.TRUE.equals(request.clearExisting()) || !this.enabled) {
                clearLocked();
            }
            return modeResponseLocked();
        }
    }

    public AiObservationModeResponse clear(UserPrincipal principal) {
        requirePlatformAdmin(principal);
        synchronized (lock) {
            clearLocked();
            return modeResponseLocked();
        }
    }

    public List<AiObservationResponse> observations(UserPrincipal principal, Integer limit) {
        requirePlatformAdmin(principal);
        synchronized (lock) {
            pruneLocked(OffsetDateTime.now());
            var boundedLimit = Math.max(1, Math.min(limit == null ? 50 : limit, properties.safeMaxEntries()));
            var result = new ArrayList<AiObservationResponse>();
            for (var callId : newestFirst) {
                var entry = entries.get(callId);
                if (entry != null) {
                    result.add(toResponse(entry));
                    if (result.size() >= boundedLimit) {
                        break;
                    }
                }
            }
            return result;
        }
    }

    private Entry entryForUpdateLocked(String callId, AiModelRequest request, OffsetDateTime observedAt) {
        if (blank(callId)) {
            return null;
        }
        var entry = entries.get(callId);
        if (entry != null) {
            return entry;
        }
        if (!enabled || request == null) {
            return null;
        }
        observeSubmitted(callId, request, observedAt);
        return entries.get(callId);
    }

    private void putNewestLocked(Entry entry) {
        entries.put(entry.callId, entry);
        newestFirst.remove(entry.callId);
        newestFirst.addFirst(entry.callId);
    }

    private void pruneLocked(OffsetDateTime now) {
        var cutoff = now.minusMinutes(properties.safeTtlMinutes());
        var removable = new LinkedHashSet<String>();
        for (var entry : entries.values()) {
            if (entry.updatedAt.isBefore(cutoff)) {
                removable.add(entry.callId);
            }
        }
        while (newestFirst.size() > properties.safeMaxEntries()) {
            removable.add(newestFirst.removeLast());
        }
        for (var callId : removable) {
            entries.remove(callId);
            newestFirst.remove(callId);
        }
    }

    private void clearLocked() {
        entries.clear();
        newestFirst.clear();
    }

    private void requirePlatformAdmin(UserPrincipal principal) {
        if (platformAdminService != null) {
            platformAdminService.requirePlatformAdmin(principal);
        }
    }

    private AiObservationModeResponse modeResponseLocked() {
        return new AiObservationModeResponse(
                enabled,
                properties.safeMaxEntries(),
                properties.safeTtlMinutes(),
                properties.safeMaxPromptChars(),
                properties.safeMaxResponseChars(),
                entries.size(),
                modeUpdatedAt);
    }

    private AiObservationResponse toResponse(Entry entry) {
        return new AiObservationResponse(
                entry.callId,
                entry.status,
                entry.providerCode,
                entry.modelId,
                entry.modelName,
                entry.officeId,
                entry.feature,
                entry.workflowType,
                entry.workflowKey,
                entry.resourceType,
                entry.resourceId,
                entry.requestOptions,
                entry.promptMessages,
                entry.promptTruncated,
                entry.responseText,
                entry.responseTruncated,
                entry.inputTokens,
                entry.outputTokens,
                entry.latencyMs,
                entry.finishReason,
                entry.providerTrace,
                entry.errorType,
                entry.errorMessage,
                entry.createdAt,
                entry.updatedAt);
    }

    private List<AiObservationMessageResponse> promptMessages(AiModelRequest request) {
        var messages = new ArrayList<AiObservationMessageResponse>();
        if (request.prompt() == null || request.prompt().messages() == null) {
            return messages;
        }
        var remaining = properties.safeMaxPromptChars();
        var truncated = false;
        for (var message : request.prompt().messages()) {
            if (remaining <= 0) {
                truncated = true;
                break;
            }
            var content = message.content() == null ? "" : message.content();
            var bounded = bounded(content, remaining);
            truncated = truncated || truncated(content, remaining);
            remaining -= bounded.length();
            messages.add(new AiObservationMessageResponse(message.role().name(), bounded));
        }
        return messages;
    }

    private boolean promptTruncated(List<AiObservationMessageResponse> messages, AiModelRequest request) {
        if (request.prompt() == null || request.prompt().messages() == null) {
            return false;
        }
        var sourceLength = request.prompt().messages().stream()
                .mapToInt(message -> message.content() == null ? 0 : message.content().length())
                .sum();
        var storedLength = messages.stream().mapToInt(message -> message.content().length()).sum();
        return sourceLength > storedLength;
    }

    private Map<String, Object> requestOptions(AiModelRequest request) {
        var values = new LinkedHashMap<String, Object>();
        if (request.options() == null) {
            return values;
        }
        request.options().asMap().forEach((key, value) -> {
            if (safeOptionKey(key) && safeOptionValue(value)) {
                values.put(key, value);
            }
        });
        return values;
    }

    private boolean safeOptionKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        var normalized = key.toLowerCase();
        return SENSITIVE_OPTION_KEYS.stream().noneMatch(normalized::contains);
    }

    private boolean safeOptionValue(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean;
    }

    private String optionText(AiModelRequest request, String key) {
        if (request.options() == null) {
            return null;
        }
        return request.options().get(key).map(String::valueOf).orElse(null);
    }

    private Long officeId(AiModelRequest request) {
        if (request.options() == null) {
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

    private String bounded(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength)) + "\n... [truncated]";
    }

    private boolean truncated(String value, int maxLength) {
        return value != null && value.length() > maxLength;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static final class Entry {
        private final String callId;
        private final String providerCode;
        private final String modelId;
        private final String modelName;
        private final Long officeId;
        private final String feature;
        private final String workflowType;
        private final String workflowKey;
        private final String resourceType;
        private final String resourceId;
        private final Map<String, Object> requestOptions;
        private final List<AiObservationMessageResponse> promptMessages;
        private final boolean promptTruncated;
        private final OffsetDateTime createdAt;
        private String status;
        private String responseText;
        private boolean responseTruncated;
        private Integer inputTokens;
        private Integer outputTokens;
        private Long latencyMs;
        private String finishReason;
        private Map<String, String> providerTrace = Map.of();
        private String errorType;
        private String errorMessage;
        private OffsetDateTime updatedAt;

        private Entry(
                String callId,
                String status,
                String providerCode,
                String modelId,
                String modelName,
                Long officeId,
                String feature,
                String workflowType,
                String workflowKey,
                String resourceType,
                String resourceId,
                Map<String, Object> requestOptions,
                List<AiObservationMessageResponse> promptMessages,
                boolean promptTruncated,
                OffsetDateTime createdAt
        ) {
            this.callId = callId;
            this.status = status;
            this.providerCode = providerCode;
            this.modelId = modelId;
            this.modelName = modelName;
            this.officeId = officeId;
            this.feature = feature;
            this.workflowType = workflowType;
            this.workflowKey = workflowKey;
            this.resourceType = resourceType;
            this.resourceId = resourceId;
            this.requestOptions = Map.copyOf(requestOptions);
            this.promptMessages = List.copyOf(promptMessages);
            this.promptTruncated = promptTruncated;
            this.createdAt = createdAt;
            this.updatedAt = createdAt;
        }

        private static Entry submitted(
                String callId,
                String providerCode,
                String modelId,
                String modelName,
                Long officeId,
                String feature,
                String workflowType,
                String workflowKey,
                String resourceType,
                String resourceId,
                Map<String, Object> requestOptions,
                List<AiObservationMessageResponse> promptMessages,
                boolean promptTruncated,
                OffsetDateTime createdAt
        ) {
            return new Entry(
                    callId,
                    "SUBMITTED",
                    providerCode,
                    modelId,
                    modelName,
                    officeId,
                    feature,
                    workflowType,
                    workflowKey,
                    resourceType,
                    resourceId,
                    requestOptions,
                    promptMessages,
                    promptTruncated,
                    createdAt);
        }
    }
}
