package com.archdox.agent.document;

import com.archdox.agent.cloud.ArchDoxAgentProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.stereotype.Component;

@Component
public class AgentDocumentStore {
    private final Path root;

    public AgentDocumentStore(ArchDoxAgentProperties properties) {
        this.root = Paths.get(properties.artifactRootPath()).toAbsolutePath().normalize();
    }

    public StoredDocument store(String logicalRef, byte[] content) throws IOException {
        var normalizedRef = normalize(logicalRef);
        var target = resolve(normalizedRef);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        return new StoredDocument(normalizedRef, target, content.length);
    }

    public byte[] read(String logicalRef) throws IOException {
        return Files.readAllBytes(resolve(normalize(logicalRef)));
    }

    public long size(String logicalRef) throws IOException {
        return Files.size(resolve(normalize(logicalRef)));
    }

    private String normalize(String logicalRef) {
        if (logicalRef == null || logicalRef.isBlank()) {
            throw new IllegalArgumentException("agent document storage reference is required");
        }
        return logicalRef.trim()
                .replace('\\', '/')
                .replaceFirst("^/+", "");
    }

    private Path resolve(String logicalRef) {
        var target = root.resolve(logicalRef).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid agent document storage reference");
        }
        return target;
    }

    public record StoredDocument(String logicalRef, Path path, long bytes) {
    }
}
