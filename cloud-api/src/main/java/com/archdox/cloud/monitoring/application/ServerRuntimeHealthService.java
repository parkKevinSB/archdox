package com.archdox.cloud.monitoring.application;

import com.archdox.cloud.monitoring.dto.ServerRuntimeHealthSnapshotResponse;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ServerRuntimeHealthService {
    public static final String EVENT_TYPE_LOAD_HIGH = "SERVER_RUNTIME_LOAD_HIGH";
    public static final String WORKFLOW_TYPE = "platform-runtime-monitor";
    public static final String WORKFLOW_KEY = "server-runtime-health";

    private final ServerRuntimeHealthProperties properties;
    private final ServerRuntimeHealthProbe probe;
    private final OperationEventService operationEventService;

    private volatile ServerRuntimeHealthSnapshotResponse latestSnapshot;
    private volatile OffsetDateTime lastHighLoadEventAt;

    public ServerRuntimeHealthService(
            ServerRuntimeHealthProperties properties,
            ServerRuntimeHealthProbe probe,
            OperationEventService operationEventService
    ) {
        this.properties = properties;
        this.probe = probe;
        this.operationEventService = operationEventService;
    }

    public ServerRuntimeHealthSnapshotResponse latestOrSample() {
        var snapshot = latestSnapshot;
        if (snapshot != null) {
            return snapshot;
        }
        return sample(OffsetDateTime.now(), false);
    }

    public ServerRuntimeHealthSnapshotResponse sample(OffsetDateTime capturedAt, boolean recordEvents) {
        var snapshot = snapshot(probe.sample(capturedAt));
        latestSnapshot = snapshot;
        if (recordEvents && "WARN".equals(snapshot.status())) {
            recordHighLoadEventIfNeeded(snapshot);
        }
        return snapshot;
    }

    private ServerRuntimeHealthSnapshotResponse snapshot(ServerRuntimeHealthMetrics metrics) {
        var systemMemoryTotal = metrics.systemMemoryTotalBytes();
        var systemMemoryFree = metrics.systemMemoryFreeBytes();
        var systemMemoryUsed = systemMemoryTotal == null || systemMemoryFree == null
                ? null
                : Math.max(0L, systemMemoryTotal - systemMemoryFree);
        var systemMemoryUsedPercent = percent(systemMemoryUsed, systemMemoryTotal);
        var jvmHeapUsedPercent = percent(metrics.jvmHeapUsedBytes(), metrics.jvmHeapMaxBytes());
        var warnings = warnings(metrics, systemMemoryUsedPercent, jvmHeapUsedPercent);
        return new ServerRuntimeHealthSnapshotResponse(
                metrics.capturedAt(),
                warnings.isEmpty() ? "OK" : "WARN",
                metrics.systemCpuLoadPercent(),
                metrics.processCpuLoadPercent(),
                metrics.systemLoadAverage(),
                metrics.availableProcessors(),
                systemMemoryTotal,
                systemMemoryUsed,
                systemMemoryUsedPercent,
                metrics.jvmHeapMaxBytes(),
                metrics.jvmHeapUsedBytes(),
                jvmHeapUsedPercent,
                warnings);
    }

    private List<String> warnings(
            ServerRuntimeHealthMetrics metrics,
            Double systemMemoryUsedPercent,
            Double jvmHeapUsedPercent
    ) {
        var warnings = new ArrayList<String>();
        var cpu = max(metrics.systemCpuLoadPercent(), metrics.processCpuLoadPercent());
        if (cpu != null && cpu >= properties.getCpuWarnPercent()) {
            warnings.add("CPU_LOAD_HIGH");
        }
        if (systemMemoryUsedPercent != null && systemMemoryUsedPercent >= properties.getSystemMemoryWarnPercent()) {
            warnings.add("SYSTEM_MEMORY_HIGH");
        }
        if (jvmHeapUsedPercent != null && jvmHeapUsedPercent >= properties.getJvmHeapWarnPercent()) {
            warnings.add("JVM_HEAP_HIGH");
        }
        return List.copyOf(warnings);
    }

    private synchronized void recordHighLoadEventIfNeeded(ServerRuntimeHealthSnapshotResponse snapshot) {
        var last = lastHighLoadEventAt;
        if (last != null && Duration.between(last, snapshot.capturedAt()).toMillis() < properties.safeEventCooldownMs()) {
            return;
        }
        lastHighLoadEventAt = snapshot.capturedAt();
        operationEventService.record(
                null,
                OperationEventSeverity.WARN,
                EVENT_TYPE_LOAD_HIGH,
                WORKFLOW_TYPE,
                WORKFLOW_KEY,
                "SERVER_RUNTIME",
                "local",
                message(snapshot),
                payload(snapshot));
    }

    private String message(ServerRuntimeHealthSnapshotResponse snapshot) {
        return "Server runtime load is high: cpu=" + percentText(max(
                snapshot.systemCpuLoadPercent(),
                snapshot.processCpuLoadPercent()))
                + ", memory=" + percentText(snapshot.systemMemoryUsedPercent())
                + ", jvmHeap=" + percentText(snapshot.jvmHeapUsedPercent())
                + ", warnings=" + String.join(",", snapshot.warnings());
    }

    private Map<String, Object> payload(ServerRuntimeHealthSnapshotResponse snapshot) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("capturedAt", snapshot.capturedAt());
        payload.put("status", snapshot.status());
        payload.put("warnings", snapshot.warnings());
        payload.put("systemCpuLoadPercent", snapshot.systemCpuLoadPercent());
        payload.put("processCpuLoadPercent", snapshot.processCpuLoadPercent());
        payload.put("systemLoadAverage", snapshot.systemLoadAverage());
        payload.put("availableProcessors", snapshot.availableProcessors());
        payload.put("systemMemoryTotalBytes", snapshot.systemMemoryTotalBytes());
        payload.put("systemMemoryUsedBytes", snapshot.systemMemoryUsedBytes());
        payload.put("systemMemoryUsedPercent", snapshot.systemMemoryUsedPercent());
        payload.put("jvmHeapMaxBytes", snapshot.jvmHeapMaxBytes());
        payload.put("jvmHeapUsedBytes", snapshot.jvmHeapUsedBytes());
        payload.put("jvmHeapUsedPercent", snapshot.jvmHeapUsedPercent());
        payload.values().removeIf(value -> value == null);
        return Map.copyOf(payload);
    }

    private Double max(Double left, Double right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.max(left, right);
    }

    private Double percent(Long used, Long total) {
        if (used == null || total == null || total <= 0L) {
            return null;
        }
        return Math.min(100.0d, (used * 100.0d) / total);
    }

    private Double percent(long used, long total) {
        if (total <= 0L) {
            return null;
        }
        return Math.min(100.0d, (used * 100.0d) / total);
    }

    private String percentText(Double value) {
        return value == null ? "-" : String.format(java.util.Locale.ROOT, "%.1f%%", value);
    }
}
