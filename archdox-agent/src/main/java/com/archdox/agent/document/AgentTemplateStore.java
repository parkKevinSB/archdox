package com.archdox.agent.document;

import com.archdox.agent.cloud.ArchDoxAgentProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AgentTemplateStore {
    private final Path root;

    public AgentTemplateStore(ArchDoxAgentProperties properties) {
        this.root = Paths.get(properties.templateRootPath()).toAbsolutePath().normalize();
    }

    public Optional<byte[]> readIfExists(String logicalRef) throws IOException {
        if (logicalRef == null || logicalRef.isBlank()) {
            return Optional.empty();
        }
        var target = resolve(normalize(logicalRef));
        if (!Files.exists(target)) {
            return Optional.empty();
        }
        return Optional.of(Files.readAllBytes(target));
    }

    public void write(String logicalRef, byte[] content) throws IOException {
        if (logicalRef == null || logicalRef.isBlank() || content == null || content.length == 0) {
            return;
        }
        var target = resolve(normalize(logicalRef));
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    private String normalize(String logicalRef) {
        return logicalRef.trim()
                .replace('\\', '/')
                .replaceFirst("^/+", "");
    }

    private Path resolve(String logicalRef) {
        var target = root.resolve(logicalRef).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid agent template storage reference");
        }
        return target;
    }
}
