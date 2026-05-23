package com.archdox.agent.photo;

import com.archdox.agent.cloud.ArchDoxAgentProperties;
import com.archdox.document.PhotoAsset;
import com.archdox.document.PhotoContentResolver;
import com.archdox.document.ResolvedPhotoContent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AgentPhotoContentResolver implements PhotoContentResolver {
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(10);

    private final ArchDoxAgentProperties properties;
    private final Path root;
    private final HttpClient httpClient;

    public AgentPhotoContentResolver(ArchDoxAgentProperties properties) {
        this.properties = properties;
        this.root = Paths.get(properties.workingRootPath()).toAbsolutePath().normalize();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public Optional<ResolvedPhotoContent> resolve(PhotoAsset photo) throws IOException {
        var cached = readIfExists(photo);
        if (cached.isPresent()) {
            return cached;
        }
        if (photo.downloadUrl() == null || photo.downloadUrl().isBlank()) {
            return Optional.empty();
        }
        var content = download(photo.downloadUrl());
        writeIfPossible(photo.storageRef(), content);
        return Optional.of(new ResolvedPhotoContent(content, photo.mimeType()));
    }

    private Optional<ResolvedPhotoContent> readIfExists(PhotoAsset photo) throws IOException {
        if (photo.storageRef() == null || photo.storageRef().isBlank()) {
            return Optional.empty();
        }
        var target = resolvePath(photo.storageRef());
        if (!Files.exists(target)) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedPhotoContent(Files.readAllBytes(target), photo.mimeType()));
    }

    private byte[] download(String downloadUrl) throws IOException {
        var uri = resolveDownloadUri(downloadUrl);
        var response = send(uri);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Photo download failed with HTTP " + response.statusCode());
        }
        return response.body();
    }

    private HttpResponse<byte[]> send(URI uri) throws IOException {
        try {
            return httpClient.send(buildRequest(uri), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Photo download was interrupted", ex);
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

    private void writeIfPossible(String storageRef, byte[] content) throws IOException {
        if (storageRef == null || storageRef.isBlank() || content == null || content.length == 0) {
            return;
        }
        var target = resolvePath(storageRef);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    private Path resolvePath(String storageRef) {
        var normalized = storageRef.trim()
                .replace('\\', '/')
                .replaceFirst("^/+", "");
        var target = root.resolve(normalized).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid agent photo storage reference");
        }
        return target;
    }
}
