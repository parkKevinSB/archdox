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
    private boolean allowSharedSecretAuth = true;

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

    public int safeInstallTokenTtlMinutes() {
        return Math.max(1, installTokenTtlMinutes);
    }
}
