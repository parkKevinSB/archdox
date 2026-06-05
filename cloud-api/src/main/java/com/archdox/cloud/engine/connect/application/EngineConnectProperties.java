package com.archdox.cloud.engine.connect.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "archdox.engine.connect")
public class EngineConnectProperties {
    private String engineApiBaseUrl = "https://api.archdox.co.kr";
    private String mcpServerUrl = "https://mcp.archdox.co.kr/api/v1/mcp";
    private int defaultKeyTtlDays = 90;
    private int maxKeyTtlDays = 365;

    public String getEngineApiBaseUrl() {
        return trimTrailingSlash(engineApiBaseUrl);
    }

    public void setEngineApiBaseUrl(String engineApiBaseUrl) {
        this.engineApiBaseUrl = engineApiBaseUrl;
    }

    public String getMcpServerUrl() {
        return trimTrailingSlash(mcpServerUrl);
    }

    public void setMcpServerUrl(String mcpServerUrl) {
        this.mcpServerUrl = mcpServerUrl;
    }

    public int getDefaultKeyTtlDays() {
        return Math.max(1, defaultKeyTtlDays);
    }

    public void setDefaultKeyTtlDays(int defaultKeyTtlDays) {
        this.defaultKeyTtlDays = defaultKeyTtlDays;
    }

    public int getMaxKeyTtlDays() {
        return Math.max(getDefaultKeyTtlDays(), maxKeyTtlDays);
    }

    public void setMaxKeyTtlDays(int maxKeyTtlDays) {
        this.maxKeyTtlDays = maxKeyTtlDays;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        var trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
