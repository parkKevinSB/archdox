package com.archdox.agent.document;

import com.archdox.agent.cloud.ArchDoxAgentProperties;
import com.archdox.document.BundledDocumentTemplates;
import com.archdox.document.TemplateContentResolver;
import com.archdox.document.TemplateSpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AgentTemplateContentResolver implements TemplateContentResolver {
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);

    private final ArchDoxAgentProperties properties;
    private final AgentTemplateStore templateStore;
    private final HttpClient httpClient;

    public AgentTemplateContentResolver(ArchDoxAgentProperties properties, AgentTemplateStore templateStore) {
        this.properties = properties;
        this.templateStore = templateStore;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public Optional<byte[]> resolve(TemplateSpec template) throws IOException {
        var bundled = BundledDocumentTemplates.read(template.storageRef());
        if (bundled.isPresent() && shouldPreferBundledTemplate(template.storageRef())) {
            templateStore.write(template.storageRef(), bundled.get());
            return bundled;
        }
        var cached = templateStore.readIfExists(template.storageRef());
        if (cached.isPresent()) {
            return cached;
        }
        if (bundled.isPresent()) {
            templateStore.write(template.storageRef(), bundled.get());
            return bundled;
        }
        if (template.downloadUrl() == null || template.downloadUrl().isBlank()) {
            return Optional.empty();
        }
        var content = download(template.downloadUrl());
        templateStore.write(template.storageRef(), content);
        return Optional.of(content);
    }

    private byte[] download(String downloadUrl) throws IOException {
        var uri = resolveDownloadUri(downloadUrl);
        var response = send(uri);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Template download failed with HTTP " + response.statusCode());
        }
        return response.body();
    }

    private HttpResponse<byte[]> send(URI uri) throws IOException {
        try {
            return httpClient.send(buildRequest(uri), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Template download was interrupted", ex);
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

    private boolean shouldPreferBundledTemplate(String storageRef) {
        if (storageRef == null || storageRef.isBlank()) {
            return false;
        }
        var normalized = storageRef.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.startsWith("templates/korean/");
    }
}
