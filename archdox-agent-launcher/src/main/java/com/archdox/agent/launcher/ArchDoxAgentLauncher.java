package com.archdox.agent.launcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class ArchDoxAgentLauncher {
    private static final int EXIT_OK = 0;
    private static final int EXIT_UPDATE_RECOMMENDED = 10;
    private static final int EXIT_UPDATE_REQUIRED = 20;
    private static final int EXIT_MANIFEST_FAILED = 30;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentUpdatePlanner planner = new AgentUpdatePlanner();

    public static void main(String[] args) throws Exception {
        var launcher = new ArchDoxAgentLauncher();
        System.exit(launcher.run(args));
    }

    int run(String[] args) throws Exception {
        var config = LauncherConfig.from(args);
        var manifest = fetchManifest(config);
        var decision = planner.decide(
                manifest,
                config.currentAgentVersion(),
                config.currentProtocolVersion(),
                config.currentLauncherVersion());
        printResult(config, manifest, decision);
        return exitCode(decision);
    }

    private AgentRuntimeManifest fetchManifest(LauncherConfig config) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(config.manifestUri())
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Agent runtime manifest request failed with HTTP " + response.statusCode());
        }
        return objectMapper.readValue(response.body(), AgentRuntimeManifest.class);
    }

    private void printResult(
            LauncherConfig config,
            AgentRuntimeManifest manifest,
            AgentUpdateDecision decision
    ) throws Exception {
        var result = new LauncherResult(
                config.cloudApiBaseUrl(),
                config.channel(),
                config.platform(),
                config.currentAgentVersion(),
                config.currentProtocolVersion(),
                config.currentLauncherVersion(),
                manifest,
                decision);
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
    }

    private int exitCode(AgentUpdateDecision decision) {
        if ("UPDATE_REQUIRED".equals(decision.status())) {
            return EXIT_UPDATE_REQUIRED;
        }
        if ("UPDATE_RECOMMENDED".equals(decision.status())) {
            return EXIT_UPDATE_RECOMMENDED;
        }
        if ("MANIFEST_UNAVAILABLE".equals(decision.status())) {
            return EXIT_MANIFEST_FAILED;
        }
        return EXIT_OK;
    }

    record LauncherConfig(
            String cloudApiBaseUrl,
            String channel,
            String platform,
            String currentAgentVersion,
            String currentProtocolVersion,
            String currentLauncherVersion
    ) {
        static LauncherConfig from(String[] args) {
            return new LauncherConfig(
                    option(args, "--cloud-api-base-url", env("ARCHDOX_CLOUD_API_BASE_URL", "http://localhost:8080")),
                    option(args, "--channel", env("ARCHDOX_AGENT_UPDATE_CHANNEL", "stable")),
                    option(args, "--platform", env("ARCHDOX_AGENT_PLATFORM", "windows-x64")),
                    option(args, "--agent-version", env("ARCHDOX_AGENT_VERSION", "0.0.1-dev")),
                    option(args, "--protocol-version", env("ARCHDOX_AGENT_PROTOCOL_VERSION", "2026-06-25")),
                    option(args, "--launcher-version", env("ARCHDOX_AGENT_LAUNCHER_VERSION", "embedded")));
        }

        URI manifestUri() {
            var base = cloudApiBaseUrl.endsWith("/") ? cloudApiBaseUrl.substring(0, cloudApiBaseUrl.length() - 1) : cloudApiBaseUrl;
            var query = "?channel=" + encode(channel) + "&platform=" + encode(platform);
            return URI.create(base + "/api/v1/archdox-agents/runtime-manifest" + query);
        }

        private static String option(String[] args, String key, String fallback) {
            if (args == null) {
                return fallback;
            }
            for (int i = 0; i < args.length - 1; i++) {
                if (key.equals(args[i])) {
                    return args[i + 1];
                }
            }
            return fallback;
        }

        private static String env(String key, String fallback) {
            var value = System.getenv(key);
            return value == null || value.isBlank() ? fallback : value.trim();
        }

        private static String encode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }

    record LauncherResult(
            String cloudApiBaseUrl,
            String channel,
            String platform,
            String currentAgentVersion,
            String currentProtocolVersion,
            String currentLauncherVersion,
            AgentRuntimeManifest manifest,
            AgentUpdateDecision decision
    ) {
    }
}
