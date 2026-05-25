package com.archdox.cloud.platformadmin.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.platform-admin.health")
public class PlatformHealthDetectionProperties {
    private long documentJobStuckMinutes = 30;
    private long agentCommandStuckMinutes = 15;
    private long photoPickupStuckMinutes = 30;
    private long deliveryStuckMinutes = 15;
    private int maxDetectedItems = 100;

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
}
