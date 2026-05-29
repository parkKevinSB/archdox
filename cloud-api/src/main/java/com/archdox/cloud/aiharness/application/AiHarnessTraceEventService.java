package com.archdox.cloud.aiharness.application;

import com.archdox.cloud.aiharness.domain.AiHarnessTraceEvent;
import com.archdox.cloud.aiharness.dto.AiHarnessTraceEventResponse;
import com.archdox.cloud.aiharness.infra.AiHarnessTraceEventRepository;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiHarnessTraceEventService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final AiHarnessTraceEventRepository repository;
    private final PlatformAdminService platformAdminService;

    public AiHarnessTraceEventService(
            AiHarnessTraceEventRepository repository,
            PlatformAdminService platformAdminService
    ) {
        this.repository = repository;
        this.platformAdminService = platformAdminService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AiHarnessTraceEventCommand command) {
        if (command == null) {
            return;
        }
        repository.save(new AiHarnessTraceEvent(
                command.officeId(),
                command.harnessRunId(),
                command.harnessId(),
                command.eventType(),
                command.status(),
                command.attempt(),
                command.modelId(),
                command.callId(),
                command.promptId(),
                command.promptVersion(),
                command.inputTokens(),
                command.outputTokens(),
                command.latencyMs(),
                command.findingCount(),
                command.validationValid(),
                command.validationErrorCount(),
                command.errorType(),
                command.message(),
                command.attributes(),
                OffsetDateTime.now()));
    }

    @Transactional(readOnly = true)
    public List<AiHarnessTraceEventResponse> traceEvents(
            UserPrincipal principal,
            String harnessRunId,
            String harnessId,
            String eventType,
            Integer limit
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        var page = PageRequest.of(0, boundedLimit(limit));
        var events = !blank(harnessRunId)
                ? repository.findByHarnessRunIdOrderByCreatedAtAscIdAsc(harnessRunId.trim(), page)
                : !blank(harnessId)
                        ? repository.findByHarnessIdOrderByCreatedAtDescIdDesc(harnessId.trim(), page)
                        : !blank(eventType)
                                ? repository.findByEventTypeOrderByCreatedAtDescIdDesc(eventType.trim().toUpperCase(), page)
                                : repository.findAllByOrderByCreatedAtDescIdDesc(page);
        return events.stream().map(this::toResponse).toList();
    }

    private AiHarnessTraceEventResponse toResponse(AiHarnessTraceEvent event) {
        return new AiHarnessTraceEventResponse(
                event.id(),
                event.officeId(),
                event.harnessRunId(),
                event.harnessId(),
                event.eventType(),
                event.status(),
                event.attempt(),
                event.modelId(),
                event.callId(),
                event.promptId(),
                event.promptVersion(),
                event.inputTokens(),
                event.outputTokens(),
                event.latencyMs(),
                event.findingCount(),
                event.validationValid(),
                event.validationErrorCount(),
                event.errorType(),
                event.message(),
                event.attributesJson(),
                event.createdAt());
    }

    private int boundedLimit(Integer limit) {
        return Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
