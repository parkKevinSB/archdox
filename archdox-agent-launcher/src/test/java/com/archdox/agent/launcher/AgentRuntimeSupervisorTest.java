package com.archdox.agent.launcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentRuntimeSupervisorTest {
    private final AgentRuntimeSupervisor supervisor = new AgentRuntimeSupervisor();

    @Test
    void startsRuntimeAndConfirmsHealth(@TempDir Path tempDir) throws Exception {
        var port = freePort();
        var server = healthServer(port);
        var current = tempDir.resolve("install/current");
        Files.createDirectories(current.resolve("bin"));
        server.start();

        AgentRuntimeStartResult result;
        try {
            result = supervisor.start(
                    tempDir.resolve("install"),
                    tempDir.resolve("work"),
                    sleepCommand(),
                    URI.create("http://127.0.0.1:" + port + "/actuator/health"),
                    Duration.ofSeconds(10),
                    true,
                    Map.of());

            assertThat(result.status()).isEqualTo("STARTED");
            assertThat(result.started()).isTrue();
            assertThat(result.healthConfirmed()).isTrue();
            assertThat(result.pid()).isNotNull();

            var status = supervisor.status(tempDir.resolve("work"), URI.create("http://127.0.0.1:" + port + "/actuator/health"));
            assertThat(status.status()).isEqualTo("RUNNING");
            assertThat(status.pidAlive()).isTrue();
        } finally {
            server.stop(0);
        }

        var stop = supervisor.stop(tempDir.resolve("work"));
        assertThat(stop.status()).isEqualTo("STOPPED");
        assertThat(stop.stopped()).isTrue();
    }

    @Test
    void rollsBackWhenRuntimeDoesNotBecomeHealthy(@TempDir Path tempDir) throws Exception {
        var installDir = tempDir.resolve("install");
        var current = installDir.resolve("current");
        var previous = installDir.resolve("previous");
        Files.createDirectories(current.resolve("bin"));
        Files.createDirectories(previous.resolve("bin"));
        Files.writeString(current.resolve("marker.txt"), "new", StandardCharsets.UTF_8);
        Files.writeString(previous.resolve("marker.txt"), "old", StandardCharsets.UTF_8);

        var result = supervisor.start(
                installDir,
                tempDir.resolve("work"),
                failCommand(),
                URI.create("http://127.0.0.1:" + freePort() + "/actuator/health"),
                Duration.ofSeconds(2),
                true,
                Map.of());

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.rollbackAttempted()).isTrue();
        assertThat(result.rolledBack()).isTrue();
        assertThat(Files.readString(installDir.resolve("current/marker.txt"))).isEqualTo("old");
    }

    private int freePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private HttpServer healthServer(int port) throws IOException {
        var server = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/actuator/health", exchange -> {
            var response = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        return server;
    }

    private String sleepCommand() {
        var windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return windows ? "powershell -NoProfile -Command Start-Sleep -Seconds 20" : "sleep 20";
    }

    private String failCommand() {
        var windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return windows ? "cmd /c exit /b 1" : "false";
    }
}
