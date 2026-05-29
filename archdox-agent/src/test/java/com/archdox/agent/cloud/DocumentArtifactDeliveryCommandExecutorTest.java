package com.archdox.agent.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.archdox.agent.document.AgentDocumentStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentArtifactDeliveryCommandExecutorTest {
    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void uploadsAgentManagedArtifactToCloudEndpoint() throws Exception {
        var objectMapper = new ObjectMapper();
        var receivedBody = new StringBuilder();
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/agent/api/v1/document-delivery-requests/77/content", exchange -> {
            assertEquals("PUT", exchange.getRequestMethod());
            assertEquals("dev-agent-secret-change-me", exchange.getRequestHeaders().getFirst("X-Agent-Token"));
            assertEquals("10", exchange.getRequestHeaders().getFirst("X-Agent-Office-Id"));
            receivedBody.append(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            var response = """
                    {
                      "deliveryRequestId": 77,
                      "artifactId": 900,
                      "preparedStorageKind": "API_LOCAL",
                      "preparedStorageRef": "deliveries/77/report.docx",
                      "bytes": 11,
                      "hashSha256": "%s"
                    }
                    """.formatted(sha256("hello-world".getBytes(StandardCharsets.UTF_8)));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            var properties = new ArchDoxAgentProperties();
            properties.setCloudHttpBaseUrl("http://localhost:" + server.getAddress().getPort());
            properties.setOfficeId(10L);
            properties.setLocalStorageRoot(tempDir.toString());
            var store = new AgentDocumentStore(properties);
            store.store("documents/jobs/700/report.docx", "hello-world".getBytes(StandardCharsets.UTF_8));
            var executor = new DocumentArtifactDeliveryCommandExecutor(properties, store, objectMapper);

            var result = executor.execute(new CloudInboundMessage(
                    "COMMAND",
                    null,
                    null,
                    null,
                    null,
                    55L,
                    "UPLOAD_DOCUMENT_ARTIFACT",
                    Map.of(
                            "deliveryRequestId", 77L,
                            "artifactId", 900L,
                            "sourceStorageRef", "documents/jobs/700/report.docx",
                            "fileName", "report.docx",
                            "mimeType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "hashSha256", sha256("hello-world".getBytes(StandardCharsets.UTF_8)),
                            "uploadUrl", "/agent/api/v1/document-delivery-requests/77/content",
                            "uploadMethod", "PUT_MULTIPART"),
                    null));

            assertEquals(77L, ((Number) result.get("deliveryRequestId")).longValue());
            assertEquals("API_LOCAL", result.get("preparedStorageKind"));
            assertTrue(receivedBody.toString().contains("hello-world"));
            assertTrue(receivedBody.toString().contains("report.docx"));
        } finally {
            server.stop(0);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
