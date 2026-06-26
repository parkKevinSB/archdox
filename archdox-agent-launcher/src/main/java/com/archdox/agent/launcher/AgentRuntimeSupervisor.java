package com.archdox.agent.launcher;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AgentRuntimeSupervisor {
    private final AgentRuntimeCommandResolver commandResolver;
    private final AgentRuntimeRollbackService rollbackService;
    private final HttpClient httpClient;

    public AgentRuntimeSupervisor() {
        this(new AgentRuntimeCommandResolver(), new AgentRuntimeRollbackService(), HttpClient.newHttpClient());
    }

    AgentRuntimeSupervisor(
            AgentRuntimeCommandResolver commandResolver,
            AgentRuntimeRollbackService rollbackService,
            HttpClient httpClient
    ) {
        this.commandResolver = commandResolver;
        this.rollbackService = rollbackService;
        this.httpClient = httpClient;
    }

    public AgentRuntimeStartResult start(
            Path installDir,
            Path workDir,
            String explicitCommand,
            URI healthUri,
            Duration startupTimeout,
            boolean rollbackOnFailure,
            Map<String, String> environment
    ) throws IOException, InterruptedException {
        var current = installDir.resolve("current");
        if (!Files.isDirectory(current)) {
            return AgentRuntimeStartResult.failed("Agent runtime current directory does not exist: " + current, false, false);
        }
        var command = commandResolver.resolve(current, explicitCommand);
        var logsDir = workDir.resolve("logs");
        Files.createDirectories(logsDir);
        Files.createDirectories(workDir);
        var stdout = logsDir.resolve("agent-runtime.out.log");
        var stderr = logsDir.resolve("agent-runtime.err.log");
        var process = startProcess(command, current, stdout, stderr, environment);
        Files.writeString(workDir.resolve("agent.pid"), String.valueOf(process.pid()));

        var healthy = waitForHealth(process, healthUri, startupTimeout);
        if (healthy) {
            return new AgentRuntimeStartResult(
                    "STARTED",
                    true,
                    true,
                    true,
                    false,
                    false,
                    process.pid(),
                    String.join(" ", command),
                    healthUri.toString(),
                    stdout.toAbsolutePath().toString(),
                    stderr.toAbsolutePath().toString(),
                    "Agent runtime started and health endpoint is UP.");
        }

        stop(process);
        var rolledBack = false;
        if (rollbackOnFailure) {
            rolledBack = rollbackService.rollback(installDir);
        }
        return new AgentRuntimeStartResult(
                "FAILED",
                true,
                false,
                false,
                rollbackOnFailure,
                rolledBack,
                process.pid(),
                String.join(" ", command),
                healthUri.toString(),
                stdout.toAbsolutePath().toString(),
                stderr.toAbsolutePath().toString(),
                rolledBack
                        ? "Agent runtime did not become healthy; rolled back to previous runtime."
                        : "Agent runtime did not become healthy.");
    }

    private Process startProcess(
            List<String> command,
            Path workingDirectory,
            Path stdout,
            Path stderr,
            Map<String, String> environment
    ) throws IOException {
        var builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(stdout.toFile()));
        builder.redirectError(ProcessBuilder.Redirect.appendTo(stderr.toFile()));
        if (environment != null && !environment.isEmpty()) {
            builder.environment().putAll(environment);
        }
        return builder.start();
    }

    private boolean waitForHealth(Process process, URI healthUri, Duration startupTimeout) throws InterruptedException {
        var deadline = System.nanoTime() + startupTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (hasExited(process)) {
                return false;
            }
            if (isHealthy(healthUri)) {
                return true;
            }
            Thread.sleep(500);
        }
        return false;
    }

    private boolean hasExited(Process process) {
        try {
            process.exitValue();
            return true;
        } catch (IllegalThreadStateException ex) {
            return false;
        }
    }

    private boolean isHealthy(URI healthUri) {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(healthUri)
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return false;
            }
            return response.body() == null
                    || response.body().isBlank()
                    || response.body().toUpperCase(Locale.ROOT).contains("\"STATUS\":\"UP\"")
                    || response.body().toUpperCase(Locale.ROOT).contains("UP");
        } catch (Exception ignored) {
            return false;
        }
    }

    private void stop(Process process) {
        if (!process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
