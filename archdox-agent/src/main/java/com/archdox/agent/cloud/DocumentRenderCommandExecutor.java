package com.archdox.agent.cloud;

import com.archdox.document.ArtifactType;
import com.archdox.document.DocumentEngine;
import com.archdox.document.DocumentGenerationRequest;
import com.archdox.document.GenerationStatus;
import com.archdox.document.OutputFormat;
import com.archdox.document.PhotoAsset;
import com.archdox.document.PhotoLayoutSize;
import com.archdox.document.TemplateSpec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.archdox.agent.document.AgentDocumentStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class DocumentRenderCommandExecutor {
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final DocumentEngine documentEngine;
    private final AgentDocumentStore agentDocumentStore;
    private final ArchDoxAgentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DocumentRenderCommandExecutor(
            DocumentEngine documentEngine,
            AgentDocumentStore agentDocumentStore,
            ArchDoxAgentProperties properties,
            ObjectMapper objectMapper
    ) {
        this.documentEngine = documentEngine;
        this.agentDocumentStore = agentDocumentStore;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public Map<String, Object> execute(CloudInboundMessage inbound) throws IOException {
        var payload = resolveRenderPayload(inbound.payload() == null ? Map.<String, Object>of() : inbound.payload());
        var jobId = stringValue(payload.get("documentJobId"), "documentJobId");
        var reportId = stringValue(payload.get("reportId"), "reportId");
        var officeId = stringValue(payload.get("officeId"), "officeId");
        var request = new DocumentGenerationRequest(
                jobId,
                officeId,
                reportId,
                templateSpec(payload.get("template")),
                mapValue(payload.get("inputSnapshot")),
                photos(payload.get("photos")),
                OutputFormat.valueOf(stringValue(payload.getOrDefault("outputFormat", "DOCX"), "outputFormat")));

        var generation = documentEngine.generate(request);
        if (generation.status() != GenerationStatus.COMPLETED) {
            throw new IllegalStateException(generation.errorMessage() == null
                    ? "Document render failed"
                    : generation.errorMessage());
        }

        var artifacts = new ArrayList<Map<String, Object>>();
        for (var artifact : generation.artifacts()) {
            if (artifact.content() == null || artifact.content().length == 0) {
                throw new IllegalStateException("Document engine returned artifact without content");
            }
            var stored = agentDocumentStore.store(artifact.storageRef(), artifact.content());
            var artifactResult = new LinkedHashMap<String, Object>();
            artifactResult.put("artifactType", artifact.type().name());
            artifactResult.put("storageKind", stringValue(payload.getOrDefault("resultStorageKind", "ARCHDOX_AGENT"), "resultStorageKind"));
            artifactResult.put("storageRef", stored.logicalRef());
            artifactResult.put("fileName", artifact.fileName());
            artifactResult.put("mimeType", mimeType(artifact.type()));
            artifactResult.put("bytes", stored.bytes());
            artifactResult.put("hashSha256", artifact.sha256());
            artifacts.add(artifactResult);
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("documentJobId", jobId);
        result.put("officeId", officeId);
        result.put("reportId", reportId);
        result.put("artifacts", artifacts);
        return result;
    }

    private Map<String, Object> resolveRenderPayload(Map<String, Object> commandPayload) throws IOException {
        var renderPackageUrl = optionalStringValue(commandPayload.get("renderPackageUrl"));
        if (renderPackageUrl == null) {
            return commandPayload;
        }
        var method = optionalStringValue(commandPayload.get("renderPackageMethod"), "GET");
        if (!"GET".equalsIgnoreCase(method)) {
            throw new IOException("Unsupported render package download method: " + method);
        }
        var renderPackage = fetchRenderPackage(renderPackageUrl);
        var merged = new LinkedHashMap<String, Object>(commandPayload);
        merged.putAll(renderPackage);
        return merged;
    }

    private Map<String, Object> fetchRenderPackage(String renderPackageUrl) throws IOException {
        var uri = resolveDownloadUri(renderPackageUrl);
        try {
            var response = httpClient.send(buildRequest(uri), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Render package download failed with HTTP " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), MAP_TYPE);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Render package download was interrupted", ex);
        }
    }

    private HttpRequest buildRequest(URI uri) {
        var builder = HttpRequest.newBuilder(uri)
                .timeout(DOWNLOAD_TIMEOUT)
                .GET();
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

    private TemplateSpec templateSpec(Object value) {
        var template = mapValue(value);
        return new TemplateSpec(
                stringValue(template.getOrDefault("templateCode", "INSPECTION_REPORT"), "templateCode"),
                intValue(template.getOrDefault("version", 1)),
                stringValue(template.getOrDefault("storageRef", "templates/default.docx"), "storageRef"),
                stringValue(template.getOrDefault("schemaJson", "{}"), "schemaJson"),
                stringValue(template.getOrDefault("composePolicyJson", "{}"), "composePolicyJson"),
                optionalStringValue(template.get("downloadUrl")),
                booleanValue(template.getOrDefault("contentRequired", false)));
    }

    private List<PhotoAsset> photos(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        var photos = new ArrayList<PhotoAsset>();
        for (var item : rawList) {
            var photo = mapValue(item);
            photos.add(new PhotoAsset(
                    stringValue(photo.get("photoId"), "photoId"),
                    optionalStringValue(photo.get("checklistItemKey"), ""),
                    optionalStringValue(photo.get("storageRef"), ""),
                    optionalStringValue(photo.get("caption"), ""),
                    PhotoLayoutSize.valueOf(optionalStringValue(photo.get("layoutSize"), "MEDIUM")),
                    optionalStringValue(photo.get("mimeType")),
                    optionalStringValue(photo.get("downloadUrl"))));
        }
        return photos;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        raw.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private String stringValue(Object value, String fieldName) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return String.valueOf(value);
    }

    private String optionalStringValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value);
    }

    private String optionalStringValue(Object value, String fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value);
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String mimeType(ArtifactType type) {
        return switch (type) {
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case HTML -> "text/html";
            case PDF -> "application/pdf";
            case HWP -> "application/x-hwp";
            case HWPX -> "application/vnd.hancom.hwpx";
            case PRINT_LOG -> "application/json";
        };
    }
}
