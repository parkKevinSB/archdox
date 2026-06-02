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
}
