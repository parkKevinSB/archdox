package com.archdox.agent.launcher;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

public class AgentPackageDownloader {
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public Path download(URI source, Path target) throws IOException, InterruptedException {
        if (source == null) {
            throw new IllegalArgumentException("Download source is required.");
        }
        Files.createDirectories(target.getParent());
        if ("file".equalsIgnoreCase(source.getScheme())) {
            Files.copy(Path.of(source), target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        }
        if (!"http".equalsIgnoreCase(source.getScheme()) && !"https".equalsIgnoreCase(source.getScheme())) {
            throw new IllegalArgumentException("Unsupported runtime package URL scheme: " + source.getScheme());
        }
        var request = HttpRequest.newBuilder()
                .uri(source)
                .timeout(Duration.ofMinutes(2))
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Files.deleteIfExists(target);
            throw new IllegalStateException("Runtime package download failed with HTTP " + response.statusCode());
        }
        return target;
    }
}
