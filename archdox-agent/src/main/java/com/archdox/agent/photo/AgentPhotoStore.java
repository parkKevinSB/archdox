package com.archdox.agent.photo;

import com.archdox.agent.cloud.ArchDoxAgentProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.stereotype.Component;

@Component
public class AgentPhotoStore {
    private final Path root;

    public AgentPhotoStore(ArchDoxAgentProperties properties) {
        this.root = Paths.get(properties.originalRootPath()).toAbsolutePath().normalize();
    }

    public StoredPhoto storeOriginal(String logicalRef, InputStream input) throws IOException {
        var normalizedRef = normalize(logicalRef);
        var target = resolve(normalizedRef);
        Files.createDirectories(target.getParent());
        var bytes = Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return new StoredPhoto(normalizedRef, target, bytes);
    }

    public void deleteIfExists(String logicalRef) throws IOException {
        Files.deleteIfExists(resolve(normalize(logicalRef)));
    }

    private String normalize(String logicalRef) {
        if (logicalRef == null || logicalRef.isBlank()) {
            throw new IllegalArgumentException("agent storage reference is required");
        }
        return logicalRef.trim()
                .replace('\\', '/')
                .replaceFirst("^/+", "");
    }

    private Path resolve(String logicalRef) {
        var target = root.resolve(logicalRef).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid agent storage reference");
        }
        return target;
    }

    public record StoredPhoto(String logicalRef, Path path, long bytes) {
    }
}
