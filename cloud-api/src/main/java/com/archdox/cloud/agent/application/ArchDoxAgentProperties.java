package com.archdox.cloud.agent.application;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.agent")
public class ArchDoxAgentProperties {
    private String apiInstanceId = "cloud-api-" + UUID.randomUUID();
    private String sharedSecret = "dev-agent-secret-change-me";
    private int commandTtlMinutes = 60;
    private int installTokenTtlMinutes = 30;
    private boolean allowSharedSecretAuth = false;
    private int websocketMaxTextMessageBufferBytes = 2 * 1024 * 1024;
    private int websocketMaxBinaryMessageBufferBytes = 2 * 1024 * 1024;
    private int websocketSendTimeLimitMs = 10_000;
    private int websocketSendBufferSizeBytes = 4 * 1024 * 1024;
    private String currentProtocolVersion = "2026-06-25";
    private String minimumProtocolVersion = "2026-06-25";
    private String minimumAgentVersion = "0.0.1-dev";
    private String recommendedAgentVersion = "0.0.1-dev";

    public String getApiInstanceId() {
        return apiInstanceId;
    }

    public void setApiInstanceId(String apiInstanceId) {
        if (apiInstanceId != null && !apiInstanceId.isBlank()) {
            this.apiInstanceId = apiInstanceId.trim();
        }
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public int getCommandTtlMinutes() {
        return commandTtlMinutes;
    }

    public void setCommandTtlMinutes(int commandTtlMinutes) {
        this.commandTtlMinutes = commandTtlMinutes;
    }

    public int getInstallTokenTtlMinutes() {
        return installTokenTtlMinutes;
    }

    public void setInstallTokenTtlMinutes(int installTokenTtlMinutes) {
        this.installTokenTtlMinutes = installTokenTtlMinutes;
    }

    public boolean isAllowSharedSecretAuth() {
        return allowSharedSecretAuth;
    }

    public void setAllowSharedSecretAuth(boolean allowSharedSecretAuth) {
        this.allowSharedSecretAuth = allowSharedSecretAuth;
    }

    public int getWebsocketMaxTextMessageBufferBytes() {
        return websocketMaxTextMessageBufferBytes;
    }

    public void setWebsocketMaxTextMessageBufferBytes(int websocketMaxTextMessageBufferBytes) {
        this.websocketMaxTextMessageBufferBytes = websocketMaxTextMessageBufferBytes;
    }

    public int getWebsocketMaxBinaryMessageBufferBytes() {
        return websocketMaxBinaryMessageBufferBytes;
    }

    public void setWebsocketMaxBinaryMessageBufferBytes(int websocketMaxBinaryMessageBufferBytes) {
        this.websocketMaxBinaryMessageBufferBytes = websocketMaxBinaryMessageBufferBytes;
    }

    public int getWebsocketSendTimeLimitMs() {
        return websocketSendTimeLimitMs;
    }

    public void setWebsocketSendTimeLimitMs(int websocketSendTimeLimitMs) {
        this.websocketSendTimeLimitMs = websocketSendTimeLimitMs;
    }

    public int getWebsocketSendBufferSizeBytes() {
        return websocketSendBufferSizeBytes;
    }

    public void setWebsocketSendBufferSizeBytes(int websocketSendBufferSizeBytes) {
        this.websocketSendBufferSizeBytes = websocketSendBufferSizeBytes;
    }

    public String getCurrentProtocolVersion() {
        return currentProtocolVersion;
    }

    public void setCurrentProtocolVersion(String currentProtocolVersion) {
        this.currentProtocolVersion = currentProtocolVersion;
    }

    public String getMinimumProtocolVersion() {
        return minimumProtocolVersion;
    }

    public void setMinimumProtocolVersion(String minimumProtocolVersion) {
        this.minimumProtocolVersion = minimumProtocolVersion;
    }

    public String getMinimumAgentVersion() {
        return minimumAgentVersion;
    }

    public void setMinimumAgentVersion(String minimumAgentVersion) {
        this.minimumAgentVersion = minimumAgentVersion;
    }

    public String getRecommendedAgentVersion() {
        return recommendedAgentVersion;
    }

    public void setRecommendedAgentVersion(String recommendedAgentVersion) {
        this.recommendedAgentVersion = recommendedAgentVersion;
    }

    public int safeInstallTokenTtlMinutes() {
        return Math.max(1, installTokenTtlMinutes);
    }

    public int safeWebsocketMaxTextMessageBufferBytes() {
        return Math.max(64 * 1024, websocketMaxTextMessageBufferBytes);
    }

    public int safeWebsocketMaxBinaryMessageBufferBytes() {
        return Math.max(64 * 1024, websocketMaxBinaryMessageBufferBytes);
    }

    public int safeWebsocketSendTimeLimitMs() {
        return Math.max(1_000, websocketSendTimeLimitMs);
    }

    public int safeWebsocketSendBufferSizeBytes() {
        return Math.max(256 * 1024, websocketSendBufferSizeBytes);
    }

    public String safeCurrentProtocolVersion() {
        return nonBlank(currentProtocolVersion, "2026-06-25");
    }

    public String safeMinimumProtocolVersion() {
        return nonBlank(minimumProtocolVersion, safeCurrentProtocolVersion());
    }

    public String safeMinimumAgentVersion() {
        return nonBlank(minimumAgentVersion, "0.0.1-dev");
    }

    public String safeRecommendedAgentVersion() {
        return nonBlank(recommendedAgentVersion, safeMinimumAgentVersion());
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
