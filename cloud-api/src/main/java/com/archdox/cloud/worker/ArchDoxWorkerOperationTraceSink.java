package com.archdox.cloud.worker;

import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.worker.application.ArchDoxWorkerTraceEvent;
import com.archdox.worker.application.ArchDoxWorkerTraceEventType;
import com.archdox.worker.application.ArchDoxWorkerTraceSink;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ArchDoxWorkerOperationTraceSink implements ArchDoxWorkerTraceSink {
    private final OperationEventService operationEventService;

    public ArchDoxWorkerOperationTraceSink(OperationEventService operationEventService) {
        this.operationEventService = operationEventService;
    }

    @Override
    public void record(ArchDoxWorkerTraceEvent event) {
        var request = event.request();
        var context = request.context();
        operationEventService.record(
                context.officeId(),
                severity(event.eventType()),
                "ARCHDOX_WORKER_" + event.eventType().name(),
                "archdox-worker",
                request.requestId().toString(),
                "ARCHDOX_WORKER_REQUEST",
                request.requestId(),
                context.userId(),
                null,
                event.message(),
                payload(event));
    }

    private OperationEventSeverity severity(ArchDoxWorkerTraceEventType eventType) {
        return switch (eventType) {
            case ACTION_FAILED, ACTION_REJECTED, ACTION_UNKNOWN, POLICY_DENIED -> OperationEventSeverity.WARN;
            default -> OperationEventSeverity.INFO;
        };
    }

    private Map<String, Object> payload(ArchDoxWorkerTraceEvent event) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("eventId", event.eventId().toString());
        payload.put("requestId", event.request().requestId().toString());
        payload.put("requestSource", event.request().source().name());
        payload.put("command", event.request().command());
        payload.put("actionType", event.action().actionType().name());
        payload.put("actionOrigin", event.action().origin().name());
        payload.put("reason", event.action().reason());
        payload.put("confidence", event.action().confidence());
        payload.put("code", event.code());
        payload.put("attributes", event.attributes());
        return payload;
    }
}
