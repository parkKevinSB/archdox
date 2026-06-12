package com.archdox.cloud.monitoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "server_runtime_health_settings")
public class ServerRuntimeHealthSettings {
    public static final String SINGLETON_KEY = "default";

    @Id
    @Column(name = "singleton_key", nullable = false, length = 64)
    private String singletonKey;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "check_interval_ms", nullable = false)
    private long checkIntervalMs;

    @Column(name = "cpu_warn_percent", nullable = false)
    private double cpuWarnPercent;

    @Column(name = "system_memory_warn_percent", nullable = false)
    private double systemMemoryWarnPercent;

    @Column(name = "jvm_heap_warn_percent", nullable = false)
    private double jvmHeapWarnPercent;

    @Column(name = "event_cooldown_ms", nullable = false)
    private long eventCooldownMs;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ServerRuntimeHealthSettings() {
    }

    public ServerRuntimeHealthSettings(
            boolean enabled,
            long checkIntervalMs,
            double cpuWarnPercent,
            double systemMemoryWarnPercent,
            double jvmHeapWarnPercent,
            long eventCooldownMs,
            Long updatedByUserId,
            OffsetDateTime now
    ) {
        this.singletonKey = SINGLETON_KEY;
        this.enabled = enabled;
        this.checkIntervalMs = checkIntervalMs;
        this.cpuWarnPercent = cpuWarnPercent;
        this.systemMemoryWarnPercent = systemMemoryWarnPercent;
        this.jvmHeapWarnPercent = jvmHeapWarnPercent;
        this.eventCooldownMs = eventCooldownMs;
        this.updatedByUserId = updatedByUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            boolean enabled,
            long checkIntervalMs,
            double cpuWarnPercent,
            double systemMemoryWarnPercent,
            double jvmHeapWarnPercent,
            long eventCooldownMs,
            Long updatedByUserId,
            OffsetDateTime now
    ) {
        this.enabled = enabled;
        this.checkIntervalMs = checkIntervalMs;
        this.cpuWarnPercent = cpuWarnPercent;
        this.systemMemoryWarnPercent = systemMemoryWarnPercent;
        this.jvmHeapWarnPercent = jvmHeapWarnPercent;
        this.eventCooldownMs = eventCooldownMs;
        this.updatedByUserId = updatedByUserId;
        this.updatedAt = now;
    }

    public boolean enabled() {
        return enabled;
    }

    public long checkIntervalMs() {
        return checkIntervalMs;
    }

    public double cpuWarnPercent() {
        return cpuWarnPercent;
    }

    public double systemMemoryWarnPercent() {
        return systemMemoryWarnPercent;
    }

    public double jvmHeapWarnPercent() {
        return jvmHeapWarnPercent;
    }

    public long eventCooldownMs() {
        return eventCooldownMs;
    }

    public Long updatedByUserId() {
        return updatedByUserId;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}
