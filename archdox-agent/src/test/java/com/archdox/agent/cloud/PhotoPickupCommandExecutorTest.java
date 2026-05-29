package com.archdox.agent.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.archdox.agent.photo.AgentPhotoStore;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PhotoPickupCommandExecutorTest {
    @Test
    void downloadsAndStoresPhotoThenReturnsCompletionResult(@TempDir Path tempDir) throws Exception {
        var content = "photo-content".getBytes();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/agent/api/v1/photos/1/assets/ORIGINAL/content", exchange -> {
            assertEquals("7", exchange.getRequestHeaders().getFirst("X-Agent-Id"));
            assertEquals("device-secret", exchange.getRequestHeaders().getFirst("X-Agent-Device-Secret"));
            assertEquals("10", exchange.getRequestHeaders().getFirst("X-Agent-Office-Id"));
            exchange.sendResponseHeaders(200, content.length);
            try (var output = exchange.getResponseBody()) {
                output.write(content);
            }
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        try {
            var properties = new ArchDoxAgentProperties();
            properties.setCloudHttpBaseUrl("http://localhost:" + server.getAddress().getPort());
            properties.setLocalStorageRoot(tempDir.toString());
            properties.setAgentId(7L);
            properties.setOfficeId(10L);
            properties.setDeviceSecret("device-secret");
            var executor = new PhotoPickupCommandExecutor(properties, new AgentPhotoStore(properties));
            var payload = Map.<String, Object>of(
                    "photoId", 1L,
                    "officeId", 10L,
                    "downloadMethod", "GET",
                    "downloadUrl", "/agent/api/v1/photos/1/assets/ORIGINAL/content",
                    "hash", sha256(content),
                    "suggestedAgentOriginalStorageRef", "offices/10/reports/100/photos/1/original.jpg",
                    "deleteTemporaryOriginal", true);

            var result = executor.execute(new CloudInboundMessage(
                    "COMMAND",
                    null,
                    null,
                    null,
                    null,
                    100L,
                    "PHOTO_PICKUP",
                    payload,
                    null));

            assertEquals(1L, result.get("photoId"));
            assertEquals("offices/10/reports/100/photos/1/original.jpg", result.get("agentOriginalStorageRef"));
            assertEquals((long) content.length, result.get("storedBytes"));
            assertTrue((Boolean) result.get("deleteTemporaryOriginal"));
            assertEquals("photo-content", Files.readString(tempDir.resolve("offices/10/reports/100/photos/1/original.jpg")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsHashMismatchAndDeletesStoredFile(@TempDir Path tempDir) throws Exception {
        var content = "photo-content".getBytes();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/object", exchange -> {
            exchange.sendResponseHeaders(200, content.length);
            try (var output = exchange.getResponseBody()) {
                new ByteArrayInputStream(content).transferTo(output);
            }
        });
        server.start();
        try {
            var properties = new ArchDoxAgentProperties();
            properties.setLocalStorageRoot(tempDir.toString());
            var executor = new PhotoPickupCommandExecutor(properties, new AgentPhotoStore(properties));
            var localRef = "offices/10/reports/100/photos/1/original.jpg";
            var payload = Map.<String, Object>of(
                    "photoId", 1L,
                    "officeId", 10L,
                    "downloadMethod", "GET",
                    "downloadUrl", "http://localhost:" + server.getAddress().getPort() + "/object",
                    "hash", "0000000000000000000000000000000000000000000000000000000000000000",
                    "suggestedAgentOriginalStorageRef", localRef);

            org.junit.jupiter.api.Assertions.assertThrows(
                    java.io.IOException.class,
                    () -> executor.execute(new CloudInboundMessage("COMMAND", null, null, null, null, 100L, "PHOTO_PICKUP", payload, null)));
            assertTrue(Files.notExists(tempDir.resolve(localRef)));
        } finally {
            server.stop(0);
        }
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
