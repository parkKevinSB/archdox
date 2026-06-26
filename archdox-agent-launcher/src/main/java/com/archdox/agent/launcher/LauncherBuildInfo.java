package com.archdox.agent.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Properties;

public record LauncherBuildInfo(
        String module,
        String version,
        String gitCommit,
        String gitBranch,
        String buildTime
) {
    public static LauncherBuildInfo current() {
        var properties = new Properties();
        try (InputStream input = LauncherBuildInfo.class.getClassLoader()
                .getResourceAsStream("META-INF/archdox/archdox-agent-launcher-build.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException ignored) {
            // Missing build metadata should not stop local runtime control.
        }
        return new LauncherBuildInfo(
                text(properties.getProperty("module"), "archdox-agent-launcher"),
                text(properties.getProperty("version"), "0.0.1-SNAPSHOT"),
                text(properties.getProperty("git.commit"), "unknown"),
                text(properties.getProperty("git.branch"), "unknown"),
                text(properties.getProperty("build.time"), OffsetDateTime.now().toString()));
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
