package com.archdox.shared.version;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Properties;

public record ArchDoxBuildInfo(
        String module,
        String version,
        String gitCommit,
        String gitBranch,
        String buildTime
) {
    private static final String UNKNOWN = "unknown";

    public static ArchDoxBuildInfo load(String moduleName, String fallbackVersion) {
        var safeModule = text(moduleName, "unknown-module");
        var properties = new Properties();
        var resource = "META-INF/archdox/" + safeModule + "-build.properties";
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ignored) {
            // Build metadata is operational context only. Missing metadata must not break runtime startup.
        }
        return new ArchDoxBuildInfo(
                text(properties.getProperty("module"), safeModule),
                text(properties.getProperty("version"), fallbackVersion),
                text(properties.getProperty("git.commit"), UNKNOWN),
                text(properties.getProperty("git.branch"), UNKNOWN),
                text(properties.getProperty("build.time"), OffsetDateTime.now().toString()));
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
