package com.archdox.cloud.monitoring.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.monitoring.domain.ServerRuntimeHealthSettings;
import com.archdox.cloud.monitoring.dto.ServerRuntimeHealthSnapshotResponse;
import com.archdox.cloud.monitoring.dto.ServerRuntimeHealthSettingsResponse;
import com.archdox.cloud.monitoring.dto.UpdateServerRuntimeHealthSettingsRequest;
import com.archdox.cloud.monitoring.infra.ServerRuntimeHealthSettingsRepository;
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
    private final ServerRuntimeHealthSettingsRepository settingsRepository;

    private volatile ServerRuntimeHealthSnapshotResponse latestSnapshot;
    private volatile OffsetDateTime lastHighLoadEventAt;

    public ServerRuntimeHealthService(
            ServerRuntimeHealthProperties properties,
            ServerRuntimeHealthProbe probe,
            OperationEventService operationEventService,
            ServerRuntimeHealthSettingsRepository settingsRepository
    ) {
        this.properties = properties;
        this.probe = probe;
        this.operationEventService = operationEventService;
        this.settingsRepository = settingsRepository;
    }

    public ServerRuntimeHealthSnapshotResponse latestOrSample() {
        var snapshot = latestSnapshot;
        if (snapshot != null) {
            return snapshot;
        }
        return sample(OffsetDateTime.now(), false);
    }

    public ServerRuntimeHealthSnapshotResponse sample(OffsetDateTime capturedAt, boolean recordEvents) {
        var settings = effectiveSettings();
        var snapshot = snapshot(probe.sample(capturedAt), settings);
        latestSnapshot = snapshot;
        if (recordEvents && settings.enabled() && "WARN".equals(snapshot.status())) {
            recordHighLoadEventIfNeeded(snapshot, settings);
        }
        return snapshot;
    }

    private ServerRuntimeHealthSnapshotResponse snapshot(
            ServerRuntimeHealthMetrics metrics,
            ServerRuntimeHealthSettingsResponse settings
    ) {
        var systemMemoryTotal = metrics.systemMemoryTotalBytes();
        var systemMemoryAvailable = firstNonNull(metrics.systemMemoryAvailableBytes(), metrics.systemMemoryFreeBytes());
        var systemMemoryUsed = systemMemoryTotal == null || systemMemoryAvailable == null
                ? null
                : Math.max(0L, systemMemoryTotal - systemMemoryAvailable);
        var systemMemoryUsedPercent = percent(systemMemoryUsed, systemMemoryTotal);
        var jvmHeapUsedPercent = percent(metrics.jvmHeapUsedBytes(), metrics.jvmHeapMaxBytes());
        var warnings = warnings(metrics, systemMemoryUsedPercent, jvmHeapUsedPercent, settings);
        return new ServerRuntimeHealthSnapshotResponse(
                metrics.capturedAt(),
                warnings.isEmpty() ? "OK" : "WARN",
                metrics.systemCpuLoadPercent(),
                metrics.processCpuLoadPercent(),
                metrics.systemLoadAverage(),
                metrics.availableProcessors(),
                systemMemoryTotal,
                systemMemoryUsed,
                systemMemoryAvailable,
                systemMemoryUsedPercent,
                metrics.jvmHeapMaxBytes(),
                metrics.jvmHeapUsedBytes(),
                jvmHeapUsedPercent,
                warnings);
    }

    private List<String> warnings(
            ServerRuntimeHealthMetrics metrics,
            Double systemMemoryUsedPercent,
            Double jvmHeapUsedPercent,
            ServerRuntimeHealthSettingsResponse settings
    ) {
        var warnings = new ArrayList<String>();
        var cpu = max(metrics.systemCpuLoadPercent(), metrics.processCpuLoadPercent());
        if (cpu != null && cpu >= settings.cpuWarnPercent()) {
            warnings.add("CPU_LOAD_HIGH");
        }
        if (systemMemoryUsedPercent != null && systemMemoryUsedPercent >= settings.systemMemoryWarnPercent()) {
            warnings.add("SYSTEM_MEMORY_HIGH");
        }
        if (jvmHeapUsedPercent != null && jvmHeapUsedPercent >= settings.jvmHeapWarnPercent()) {
            warnings.add("JVM_HEAP_HIGH");
        }
        return List.copyOf(warnings);
    }

    private synchronized void recordHighLoadEventIfNeeded(
            ServerRuntimeHealthSnapshotResponse snapshot,
            ServerRuntimeHealthSettingsResponse settings
    ) {
        var last = lastHighLoadEventAt;
        if (last != null && Duration.between(last, snapshot.capturedAt()).toMillis() < safeEventCooldownMs(settings)) {
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
        payload.put("systemMemoryAvailableBytes", snapshot.systemMemoryAvailableBytes());
        payload.put("systemMemoryUsedPercent", snapshot.systemMemoryUsedPercent());
        payload.put("jvmHeapMaxBytes", snapshot.jvmHeapMaxBytes());
        payload.put("jvmHeapUsedBytes", snapshot.jvmHeapUsedBytes());
        payload.put("jvmHeapUsedPercent", snapshot.jvmHeapUsedPercent());
        payload.values().removeIf(value -> value == null);
        return Map.copyOf(payload);
    }

    public ServerRuntimeHealthSettingsResponse settings() {
        return effectiveSettings();
    }

    public long effectiveCheckIntervalMs() {
        var settings = effectiveSettings();
        if (!settings.enabled()) {
            return properties.safeCheckIntervalMs();
        }
        return Math.max(30_000L, settings.checkIntervalMs());
    }

    public boolean monitoringEnabled() {
        return effectiveSettings().enabled();
    }

    public ServerRuntimeHealthSettingsResponse updateSettings(
            UpdateServerRuntimeHealthSettingsRequest request,
            Long updatedByUserId
    ) {
        var now = OffsetDateTime.now();
        var current = effectiveSettings();
        var enabled = request.enabled() == null ? current.enabled() : request.enabled();
        var checkIntervalMs = normalizeMillis(request.checkIntervalMs(), current.checkIntervalMs(), 30_000L, 86_400_000L, "checkIntervalMs");
        var cpuWarnPercent = normalizePercent(request.cpuWarnPercent(), current.cpuWarnPercent(), "cpuWarnPercent");
        var systemMemoryWarnPercent = normalizePercent(request.systemMemoryWarnPercent(), current.systemMemoryWarnPercent(), "systemMemoryWarnPercent");
        var jvmHeapWarnPercent = normalizePercent(request.jvmHeapWarnPercent(), current.jvmHeapWarnPercent(), "jvmHeapWarnPercent");
        var eventCooldownMs = normalizeMillis(request.eventCooldownMs(), current.eventCooldownMs(), 60_000L, 86_400_000L, "eventCooldownMs");
        var settings = settingsRepository.findById(ServerRuntimeHealthSettings.SINGLETON_KEY)
                .orElseGet(() -> new ServerRuntimeHealthSettings(
                        enabled,
                        checkIntervalMs,
                        cpuWarnPercent,
                        systemMemoryWarnPercent,
                        jvmHeapWarnPercent,
                        eventCooldownMs,
                        updatedByUserId,
                        now));
        settings.update(
                enabled,
                checkIntervalMs,
                cpuWarnPercent,
                systemMemoryWarnPercent,
                jvmHeapWarnPercent,
                eventCooldownMs,
                updatedByUserId,
                now);
        return ServerRuntimeHealthSettingsResponse.from(settingsRepository.save(settings));
    }

    private ServerRuntimeHealthSettingsResponse effectiveSettings() {
        return settingsRepository.findById(ServerRuntimeHealthSettings.SINGLETON_KEY)
                .map(ServerRuntimeHealthSettingsResponse::from)
                .orElseGet(() -> ServerRuntimeHealthSettingsResponse.fromDefaults(properties));
    }

    private long normalizeMillis(Long value, long fallback, long min, long max, String field) {
        if (value == null) {
            return fallback;
        }
        if (value < min || value > max) {
            throw new BadRequestException(field + " must be between " + min + " and " + max + " ms.");
        }
        return value;
    }

    private double normalizePercent(Double value, double fallback, String field) {
        if (value == null) {
            return fallback;
        }
        if (Double.isNaN(value) || value < 1.0d || value > 100.0d) {
            throw new BadRequestException(field + " must be between 1 and 100.");
        }
        return value;
    }

    private long safeEventCooldownMs(ServerRuntimeHealthSettingsResponse settings) {
        return Math.max(60_000L, settings.eventCooldownMs());
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

    private Long firstNonNull(Long first, Long second) {
        return first == null ? second : first;
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
