package com.archdox.cloud.agent.application;

import com.archdox.shared.version.ArchDoxBuildInfo;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.agent")
public class ArchDoxAgentProperties {
    private final ArchDoxBuildInfo buildInfo = ArchDoxBuildInfo.load("cloud-api", "0.0.1-SNAPSHOT");
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
    private String minimumAgentVersion;
    private String recommendedAgentVersion;
    private String latestAgentVersion;
    private String minimumLauncherVersion = "embedded";
    private String recommendedLauncherVersion = "embedded";
    private String runtimePackageDownloadUrl;
    private String runtimePackageSha256;
    private String runtimePackageSignatureUrl;
    private String runtimeReleaseNotesUrl;

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

    public String getLatestAgentVersion() {
        return latestAgentVersion;
    }

    public void setLatestAgentVersion(String latestAgentVersion) {
        this.latestAgentVersion = latestAgentVersion;
    }

    public String getMinimumLauncherVersion() {
        return minimumLauncherVersion;
    }

    public void setMinimumLauncherVersion(String minimumLauncherVersion) {
        this.minimumLauncherVersion = minimumLauncherVersion;
    }

    public String getRecommendedLauncherVersion() {
        return recommendedLauncherVersion;
    }

    public void setRecommendedLauncherVersion(String recommendedLauncherVersion) {
        this.recommendedLauncherVersion = recommendedLauncherVersion;
    }

    public String getRuntimePackageDownloadUrl() {
        return runtimePackageDownloadUrl;
    }

    public void setRuntimePackageDownloadUrl(String runtimePackageDownloadUrl) {
        this.runtimePackageDownloadUrl = runtimePackageDownloadUrl;
    }

    public String getRuntimePackageSha256() {
        return runtimePackageSha256;
    }

    public void setRuntimePackageSha256(String runtimePackageSha256) {
        this.runtimePackageSha256 = runtimePackageSha256;
    }

    public String getRuntimePackageSignatureUrl() {
        return runtimePackageSignatureUrl;
    }

    public void setRuntimePackageSignatureUrl(String runtimePackageSignatureUrl) {
        this.runtimePackageSignatureUrl = runtimePackageSignatureUrl;
    }

    public String getRuntimeReleaseNotesUrl() {
        return runtimeReleaseNotesUrl;
    }

    public void setRuntimeReleaseNotesUrl(String runtimeReleaseNotesUrl) {
        this.runtimeReleaseNotesUrl = runtimeReleaseNotesUrl;
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
        return nonBlank(minimumAgentVersion, buildInfo.version());
    }

    public String safeRecommendedAgentVersion() {
        return nonBlank(recommendedAgentVersion, safeMinimumAgentVersion());
    }

    public String safeLatestAgentVersion() {
        return nonBlank(latestAgentVersion, safeRecommendedAgentVersion());
    }

    public String safeMinimumLauncherVersion() {
        return nonBlank(minimumLauncherVersion, "embedded");
    }

    public String safeRecommendedLauncherVersion() {
        return nonBlank(recommendedLauncherVersion, safeMinimumLauncherVersion());
    }

    public String optionalRuntimePackageDownloadUrl() {
        return blankToNull(runtimePackageDownloadUrl);
    }

    public String optionalRuntimePackageSha256() {
        return blankToNull(runtimePackageSha256);
    }

    public String optionalRuntimePackageSignatureUrl() {
        return blankToNull(runtimePackageSignatureUrl);
    }

    public String optionalRuntimeReleaseNotesUrl() {
        return blankToNull(runtimeReleaseNotesUrl);
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
