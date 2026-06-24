package com.archdox.cloud.platformops.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.platform-admin.health")
public class PlatformOpsDetectionProperties {
    private boolean enabled = true;
    private long documentJobStuckMinutes = 30;
    private long agentCommandStuckMinutes = 15;
    private long photoPickupStuckMinutes = 30;
    private long deliveryStuckMinutes = 15;
    private int maxDetectedItems = 100;
    private long workerIntervalMs = 300_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getDocumentJobStuckMinutes() {
        return documentJobStuckMinutes;
    }

    public void setDocumentJobStuckMinutes(long documentJobStuckMinutes) {
        this.documentJobStuckMinutes = documentJobStuckMinutes;
    }

    public long getAgentCommandStuckMinutes() {
        return agentCommandStuckMinutes;
    }

    public void setAgentCommandStuckMinutes(long agentCommandStuckMinutes) {
        this.agentCommandStuckMinutes = agentCommandStuckMinutes;
    }

    public long getPhotoPickupStuckMinutes() {
        return photoPickupStuckMinutes;
    }

    public void setPhotoPickupStuckMinutes(long photoPickupStuckMinutes) {
        this.photoPickupStuckMinutes = photoPickupStuckMinutes;
    }

    public long getDeliveryStuckMinutes() {
        return deliveryStuckMinutes;
    }

    public void setDeliveryStuckMinutes(long deliveryStuckMinutes) {
        this.deliveryStuckMinutes = deliveryStuckMinutes;
    }

    public int getMaxDetectedItems() {
        return maxDetectedItems;
    }

    public void setMaxDetectedItems(int maxDetectedItems) {
        this.maxDetectedItems = maxDetectedItems;
    }

    public long getWorkerIntervalMs() {
        return workerIntervalMs;
    }

    public void setWorkerIntervalMs(long workerIntervalMs) {
        this.workerIntervalMs = workerIntervalMs;
    }

    public long safeWorkerIntervalMs() {
        return Math.max(10_000, workerIntervalMs);
    }
}
