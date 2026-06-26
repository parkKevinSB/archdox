package com.archdox.cloud.agent.application;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ArchDoxAgentReleaseUrlFactory {
    private final ArchDoxAgentReleaseProperties properties;

    public ArchDoxAgentReleaseUrlFactory(ArchDoxAgentReleaseProperties properties) {
        this.properties = properties;
    }

    public String runtimePackageUrl(String channel, String platform, String version) {
        return packageUrl(properties.safeRuntimePathTemplate(), channel, platform, version);
    }

    public String launcherPackageUrl(String channel, String platform, String version) {
        return packageUrl(properties.safeLauncherPathTemplate(), channel, platform, version);
    }

    private String packageUrl(String template, String channel, String platform, String version) {
        var baseUrl = properties.optionalPublicBaseUrl();
        if (baseUrl == null) {
            return null;
        }
        var path = replace(template, Map.of(
                "channel", safe(channel),
                "platform", safe(platform),
                "version", safe(version)));
        var prefix = properties.safeObjectPrefix();
        var fullPath = prefix.isBlank() ? trimSlashes(path) : prefix + "/" + trimSlashes(path);
        return trimTrailingSlash(baseUrl) + "/" + fullPath;
    }

    private String replace(String template, Map<String, String> values) {
        var result = template;
        for (var entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private String trimTrailingSlash(String value) {
        var result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String trimSlashes(String value) {
        var result = value == null ? "" : value.trim();
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
