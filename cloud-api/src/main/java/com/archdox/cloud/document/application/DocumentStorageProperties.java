package com.archdox.cloud.document.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.documents.storage")
public class DocumentStorageProperties {
    private String localRoot = "build/document-storage";
    private int deliveryPreparedTtlMinutes = 60;

    public String getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public int getDeliveryPreparedTtlMinutes() {
        return deliveryPreparedTtlMinutes;
    }

    public void setDeliveryPreparedTtlMinutes(int deliveryPreparedTtlMinutes) {
        this.deliveryPreparedTtlMinutes = deliveryPreparedTtlMinutes;
    }

    public int safeDeliveryPreparedTtlMinutes() {
        return Math.max(1, deliveryPreparedTtlMinutes);
    }
}
