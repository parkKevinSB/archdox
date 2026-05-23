package com.archdox.agent.cloud;

import com.archdox.agent.photo.AgentPhotoStore;
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
import org.springframework.stereotype.Component;

@Component
public class PhotoPickupCommandExecutor {
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(10);

    private final ArchDoxAgentProperties properties;
    private final AgentPhotoStore agentPhotoStore;
    private final HttpClient httpClient;

    public PhotoPickupCommandExecutor(ArchDoxAgentProperties properties, AgentPhotoStore agentPhotoStore) {
        this.properties = properties;
        this.agentPhotoStore = agentPhotoStore;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public Map<String, Object> execute(CloudInboundMessage inbound) throws IOException, InterruptedException {
        var payload = inbound.payload() == null ? Map.<String, Object>of() : inbound.payload();
        var photoId = longValue(payload.get("photoId"), "photoId");
        var downloadMethod = stringValue(payload.getOrDefault("downloadMethod", "GET"));
        if (!"GET".equalsIgnoreCase(downloadMethod)) {
            throw new IllegalArgumentException("Unsupported PHOTO_PICKUP download method: " + downloadMethod);
        }
        var downloadUrl = stringValue(payload.get("downloadUrl"));
        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new IllegalArgumentException("downloadUrl is required");
        }
        var agentOriginalStorageRef = firstText(
                stringValue(payload.get("suggestedAgentOriginalStorageRef")),
                stringValue(payload.get("sourceStorageRef")),
                "photos/%d/original.bin".formatted(photoId));
        var uri = resolveDownloadUri(downloadUrl);
        var request = buildRequest(uri, payload);
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Photo download failed with HTTP " + response.statusCode());
        }

        var digest = sha256();
        AgentPhotoStore.StoredPhoto stored;
        try (var input = new java.security.DigestInputStream(response.body(), digest)) {
            stored = agentPhotoStore.storeOriginal(agentOriginalStorageRef, input);
        }
        validateHashIfPossible(stringValue(payload.get("hash")), digest, stored.logicalRef());

        var result = new LinkedHashMap<String, Object>();
        result.put("photoId", photoId);
        result.put("officeId", longValue(payload.get("officeId"), "officeId"));
        result.put("agentOriginalStorageRef", stored.logicalRef());
        result.put("storedBytes", stored.bytes());
        result.put("deleteTemporaryOriginal", booleanValue(payload.getOrDefault("deleteTemporaryOriginal", true)));
        return result;
    }

    private HttpRequest buildRequest(URI uri, Map<String, Object> payload) {
        var builder = HttpRequest.newBuilder(uri)
                .timeout(DOWNLOAD_TIMEOUT)
                .GET();
        mapValue(payload.get("downloadHeaders")).forEach((name, value) -> {
            if (name != null && value != null && !name.isBlank()) {
                builder.header(name, value);
            }
        });
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

    private URI resolveDownloadUri(String downloadUrl) {
        var uri = URI.create(downloadUrl);
        if (uri.isAbsolute()) {
            return uri;
        }
        return URI.create(properties.getCloudHttpBaseUrl()).resolve(uri);
    }

    private boolean requiresAgentAuth(URI uri) {
        return uri.getPath() != null && uri.getPath().startsWith("/agent/api/");
    }

    private void validateHashIfPossible(String expectedHash, MessageDigest digest, String logicalRef) throws IOException {
        var normalized = normalizeHash(expectedHash);
        if (normalized == null) {
            return;
        }
        var actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equalsIgnoreCase(normalized)) {
            agentPhotoStore.deleteIfExists(logicalRef);
            throw new IOException("Downloaded photo hash does not match command payload");
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

    private Long longValue(Object value, String fieldName) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.valueOf(text);
        }
        throw new IllegalArgumentException(fieldName + " is required");
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, String>();
        raw.forEach((key, mapValue) -> result.put(String.valueOf(key), String.valueOf(mapValue)));
        return result;
    }

    private String firstText(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }
}
