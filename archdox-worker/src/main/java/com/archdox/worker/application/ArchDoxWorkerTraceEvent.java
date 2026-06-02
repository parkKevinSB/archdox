package com.archdox.worker.application;

import com.archdox.worker.domain.ArchDoxWorkerAction;
import com.archdox.worker.domain.ArchDoxWorkerRequest;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ArchDoxWorkerTraceEvent(
        UUID eventId,
        Instant occurredAt,
        ArchDoxWorkerTraceEventType eventType,
        ArchDoxWorkerRequest request,
        ArchDoxWorkerAction action,
        String code,
        String message,
        Map<String, Object> attributes
) {
    public ArchDoxWorkerTraceEvent {
        eventId = Objects.requireNonNullElseGet(eventId, UUID::randomUUID);
        occurredAt = Objects.requireNonNullElseGet(occurredAt, Instant::now);
        Objects.requireNonNull(eventType, "eventType must not be null");
        code = code == null ? "" : code.trim();
        message = message == null ? "" : message.trim();
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static ArchDoxWorkerTraceEvent of(
            ArchDoxWorkerTraceEventType eventType,
            ArchDoxWorkerRequest request,
            ArchDoxWorkerAction action,
            String code,
            String message
    ) {
        return new ArchDoxWorkerTraceEvent(UUID.randomUUID(), Instant.now(), eventType, request, action, code, message, Map.of());
    }

    public static ArchDoxWorkerTraceEvent of(
            ArchDoxWorkerTraceEventType eventType,
            ArchDoxWorkerRequest request,
            ArchDoxWorkerAction action,
            String code,
            String message,
            Map<String, Object> attributes
    ) {
        return new ArchDoxWorkerTraceEvent(UUID.randomUUID(), Instant.now(), eventType, request, action, code, message, attributes);
    }
}
