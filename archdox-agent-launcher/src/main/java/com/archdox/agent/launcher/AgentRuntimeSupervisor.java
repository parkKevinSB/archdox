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
import java.util.concurrent.TimeUnit;

public class AgentRuntimeSupervisor {
    private final AgentRuntimeCommandResolver commandResolver;
    private final AgentRuntimeRollbackService rollbackService;
    private final AgentRuntimeStateStore stateStore;
    private final HttpClient httpClient;

    public AgentRuntimeSupervisor() {
        this(
                new AgentRuntimeCommandResolver(),
                new AgentRuntimeRollbackService(),
                new AgentRuntimeStateStore(),
                HttpClient.newHttpClient());
    }

    AgentRuntimeSupervisor(
            AgentRuntimeCommandResolver commandResolver,
            AgentRuntimeRollbackService rollbackService,
            AgentRuntimeStateStore stateStore,
            HttpClient httpClient
    ) {
        this.commandResolver = commandResolver;
        this.rollbackService = rollbackService;
        this.stateStore = stateStore;
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
        var commandText = String.join(" ", command);
        stateStore.write(workDir, stateStore.state(
                "STARTING",
                process.pid(),
                commandText,
                healthUri.toString(),
                stdout.toAbsolutePath().toString(),
                stderr.toAbsolutePath().toString(),
                "Agent runtime process started; waiting for health."));

        var healthy = waitForHealth(process, healthUri, startupTimeout);
        if (healthy) {
            stateStore.write(workDir, stateStore.state(
                    "STARTED",
                    process.pid(),
                    commandText,
                    healthUri.toString(),
                    stdout.toAbsolutePath().toString(),
                    stderr.toAbsolutePath().toString(),
                    "Agent runtime started and health endpoint is UP."));
            return new AgentRuntimeStartResult(
                    "STARTED",
                    true,
                    true,
                    true,
                    false,
                    false,
                    process.pid(),
                    commandText,
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
        stateStore.write(workDir, stateStore.state(
                "FAILED",
                process.pid(),
                commandText,
                healthUri.toString(),
                stdout.toAbsolutePath().toString(),
                stderr.toAbsolutePath().toString(),
                rolledBack
                        ? "Agent runtime did not become healthy; rolled back to previous runtime."
                        : "Agent runtime did not become healthy."));
        return new AgentRuntimeStartResult(
                "FAILED",
                true,
                false,
                false,
                rollbackOnFailure,
                rolledBack,
                process.pid(),
                commandText,
                healthUri.toString(),
                stdout.toAbsolutePath().toString(),
                stderr.toAbsolutePath().toString(),
                rolledBack
                        ? "Agent runtime did not become healthy; rolled back to previous runtime."
                        : "Agent runtime did not become healthy.");
    }

    public AgentRuntimeStopResult stop(Path workDir) throws IOException {
        var pid = pid(workDir);
        if (pid == null) {
            stateStore.write(workDir, stateStore.state(
                    "STOPPED",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "No Agent runtime pid is recorded."));
            return AgentRuntimeStopResult.notRunning("No Agent runtime pid is recorded.");
        }
        var handle = ProcessHandle.of(pid);
        if (handle.isEmpty() || !handle.get().isAlive()) {
            stateStore.write(workDir, stateStore.state(
                    "STOPPED",
                    pid,
                    null,
                    null,
                    null,
                    null,
                    "Recorded Agent runtime process is not running."));
            return new AgentRuntimeStopResult(
                    "NOT_RUNNING",
                    false,
                    false,
                    pid,
                    "Recorded Agent runtime process is not running.");
        }
        stopHandle(handle.get());
        var stopped = !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        stateStore.write(workDir, stateStore.state(
                stopped ? "STOPPED" : "STOP_FAILED",
                pid,
                null,
                null,
                null,
                null,
                stopped ? "Agent runtime process stopped." : "Agent runtime process is still alive after stop."));
        return new AgentRuntimeStopResult(
                stopped ? "STOPPED" : "FAILED",
                true,
                stopped,
                pid,
                stopped ? "Agent runtime process stopped." : "Agent runtime process is still alive after stop.");
    }

    public AgentRuntimeStatusResult status(Path workDir, URI healthUri) throws IOException {
        var state = stateStore.read(workDir).orElse(null);
        var pid = pid(workDir);
        var pidAlive = pid != null && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        var healthy = pidAlive && healthUri != null && isHealthy(healthUri);
        var status = healthy ? "RUNNING"
                : pidAlive ? "UNHEALTHY"
                : pid != null ? "STOPPED"
                : "UNKNOWN";
        var reason = switch (status) {
            case "RUNNING" -> "Agent runtime process is alive and health endpoint is UP.";
            case "UNHEALTHY" -> "Agent runtime process is alive but health endpoint is not UP.";
            case "STOPPED" -> "Agent runtime pid is recorded but process is not running.";
            default -> "No Agent runtime pid is recorded.";
        };
        return new AgentRuntimeStatusResult(
                status,
                pid != null,
                pidAlive,
                healthy,
                pid,
                healthUri == null ? null : healthUri.toString(),
                state,
                reason);
    }

    public AgentRuntimeStartResult supervise(
            Path installDir,
            Path workDir,
            String explicitCommand,
            URI healthUri,
            Duration startupTimeout,
            boolean rollbackOnFailure,
            Duration monitorInterval,
            long maxRestarts,
            Map<String, String> environment
    ) throws IOException, InterruptedException {
        long restarts = 0;
        AgentRuntimeStartResult lastStart = AgentRuntimeStartResult.notAttempted("Supervisor has not started the runtime yet.");
        while (true) {
            var status = status(workDir, healthUri);
            if (!status.pidAlive() || !status.healthConfirmed()) {
                if (status.pidAlive()) {
                    stop(workDir);
                }
                if (maxRestarts > 0 && restarts >= maxRestarts) {
                    return new AgentRuntimeStartResult(
                            "FAILED",
                            true,
                            false,
                            false,
                            rollbackOnFailure,
                            false,
                            status.pid(),
                            null,
                            healthUri.toString(),
                            null,
                            null,
                            "Supervisor restart limit reached: " + maxRestarts);
                }
                lastStart = start(
                        installDir,
                        workDir,
                        explicitCommand,
                        healthUri,
                        startupTimeout,
                        rollbackOnFailure,
                        environment);
                restarts++;
                if (!lastStart.started()) {
                    Thread.sleep(monitorInterval.toMillis());
                    continue;
                }
            }
            Thread.sleep(monitorInterval.toMillis());
        }
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
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private void stopHandle(ProcessHandle handle) {
        handle.descendants().forEach(ProcessHandle::destroy);
        handle.destroy();
        try {
            handle.onExit().get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            handle.descendants().forEach(ProcessHandle::destroyForcibly);
            handle.destroyForcibly();
        }
    }

    private Long pid(Path workDir) throws IOException {
        var statePid = stateStore.read(workDir).map(AgentRuntimeState::pid).orElse(null);
        if (statePid != null) {
            return statePid;
        }
        var pidFile = workDir.resolve("agent.pid");
        if (!Files.isRegularFile(pidFile)) {
            return null;
        }
        var value = Files.readString(pidFile).trim();
        if (value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
