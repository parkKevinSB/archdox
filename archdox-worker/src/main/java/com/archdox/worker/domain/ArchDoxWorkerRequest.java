package com.archdox.worker.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ArchDoxWorkerRequest(
        UUID requestId,
        ArchDoxWorkerRequestSource source,
        String command,
        ArchDoxWorkerRequestContext context,
        Instant requestedAt
) {
    public ArchDoxWorkerRequest {
        requestId = Objects.requireNonNullElseGet(requestId, UUID::randomUUID);
        source = source == null ? ArchDoxWorkerRequestSource.SYSTEM : source;
        command = command == null ? "" : command.trim();
        context = context == null ? ArchDoxWorkerRequestContext.empty() : context;
        requestedAt = Objects.requireNonNullElseGet(requestedAt, Instant::now);
    }

    public static ArchDoxWorkerRequest fromUi(String command, ArchDoxWorkerRequestContext context) {
        return new ArchDoxWorkerRequest(UUID.randomUUID(), ArchDoxWorkerRequestSource.UI, command, context, Instant.now());
    }
}
