package com.archdox.agent.cloud;

import com.archdox.agent.document.AgentDocumentStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DocumentArtifactDeliveryCommandExecutor {
    private static final Duration UPLOAD_TIMEOUT = Duration.ofMinutes(10);

    private final ArchDoxAgentProperties properties;
    private final AgentDocumentStore agentDocumentStore;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DocumentArtifactDeliveryCommandExecutor(
            ArchDoxAgentProperties properties,
            AgentDocumentStore agentDocumentStore,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.agentDocumentStore = agentDocumentStore;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public Map<String, Object> execute(CloudInboundMessage inbound) throws IOException, InterruptedException {
        var payload = inbound.payload() == null ? Map.<String, Object>of() : inbound.payload();
        var uploadMethod = stringValue(payload.getOrDefault("uploadMethod", "PUT_MULTIPART"), "uploadMethod");
        if (!"PUT_MULTIPART".equalsIgnoreCase(uploadMethod)) {
            throw new IllegalArgumentException("Unsupported document delivery upload method: " + uploadMethod);
        }
        var uploadUrl = stringValue(payload.get("uploadUrl"), "uploadUrl");
        var sourceStorageRef = stringValue(payload.get("sourceStorageRef"), "sourceStorageRef");
        var fileName = stringValue(payload.getOrDefault("fileName", "document-artifact.bin"), "fileName");
        var mimeType = stringValue(payload.getOrDefault("mimeType", "application/octet-stream"), "mimeType");
        var content = agentDocumentStore.read(sourceStorageRef);
        validateHashIfPossible(stringValueOrNull(payload.get("hashSha256")), content);

        var boundary = "archdox-" + UUID.randomUUID();
        var request = buildRequest(
                resolveUploadUri(uploadUrl),
                inbound.commandId(),
                payload,
                boundary,
                multipartBody(boundary, fileName, mimeType, content));
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Document artifact upload failed with HTTP " + response.statusCode());
        }
        var result = objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {
        });
        result.putIfAbsent("deliveryRequestId", payload.get("deliveryRequestId"));
        result.putIfAbsent("artifactId", payload.get("artifactId"));
        result.putIfAbsent("uploadedBytes", content.length);
        return result;
    }

    private HttpRequest buildRequest(
            URI uri,
            Long commandId,
            Map<String, Object> payload,
            String boundary,
            byte[] body
    ) {
        var builder = HttpRequest.newBuilder(uri)
                .timeout(UPLOAD_TIMEOUT)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Agent-Command-Id", String.valueOf(commandId));
        if (requiresAgentAuth(uri)) {
            if (properties.getAgentId() != null
                    && properties.getDeviceSecret() != null
                    && !properties.getDeviceSecret().isBlank()) {
                builder.header("X-Agent-Id", String.valueOf(properties.getAgentId()));
                builder.header("X-Agent-Device-Secret", properties.getDeviceSecret());
            } else {
                builder.header("X-Agent-Token", properties.getToken());
            }
            builder.header("X-Agent-Office-Id", String.valueOf(properties.getOfficeId()));
        }
        return builder.build();
    }

    private URI resolveUploadUri(String uploadUrl) {
        var uri = URI.create(uploadUrl);
        if (uri.isAbsolute()) {
            return uri;
        }
        return URI.create(properties.getCloudHttpBaseUrl()).resolve(uri);
    }

    private boolean requiresAgentAuth(URI uri) {
        return uri.getPath() != null && uri.getPath().startsWith("/agent/api/");
    }

    private byte[] multipartBody(String boundary, String fileName, String mimeType, byte[] content) throws IOException {
        var header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\""
                + safeMultipartFileName(fileName)
                + "\"\r\n"
                + "Content-Type: " + mimeType + "\r\n"
                + "\r\n";
        var footer = "\r\n--" + boundary + "--\r\n";
        var output = new java.io.ByteArrayOutputStream();
        output.write(header.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.write(content);
        output.write(footer.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private String safeMultipartFileName(String fileName) {
        return fileName.replace("\\", "_").replace("/", "_").replace("\"", "_");
    }

    private void validateHashIfPossible(String expectedHash, byte[] content) throws IOException {
        var normalized = normalizeHash(expectedHash);
        if (normalized == null) {
            return;
        }
        var actual = HexFormat.of().formatHex(sha256().digest(content));
        if (!actual.equalsIgnoreCase(normalized)) {
            throw new IOException("Document artifact hash does not match command payload");
        }
    }

    private String normalizeHash(String expectedHash) {
        if (expectedHash == null || expectedHash.isBlank()) {
            return null;
        }
        var text = expectedHash.trim().toLowerCase(java.util.Locale.ROOT);
        if (text.startsWith("sha256:")) {
            text = text.substring("sha256:".length());
        }
        return text.matches("[0-9a-f]{64}") ? text : null;
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }

    private String stringValue(Object value, String fieldName) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return String.valueOf(value);
    }

    private String stringValueOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
