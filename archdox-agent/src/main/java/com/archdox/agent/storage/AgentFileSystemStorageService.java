package com.archdox.agent.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Optional;

public class AgentFileSystemStorageService implements AgentStorageService {
    private final AgentStorageKind kind;
    private final AgentFileSystemStorageSupport storage;

    public AgentFileSystemStorageService(String usage, AgentStorageTargetProfile profile) {
        this.kind = profile.kind();
        this.storage = new AgentFileSystemStorageSupport(usage, profile);
    }

    @Override
    public AgentStorageKind kind() {
        return kind;
    }

    @Override
    public String normalize(String logicalRef) {
        return storage.normalize(logicalRef);
    }

    @Override
    public AgentStoredObject put(String logicalRef, InputStream input, String contentType) throws IOException {
        var normalizedRef = normalize(logicalRef);
        var target = storage.resolve(normalizedRef);
        Files.createDirectories(target.getParent());
        var bytes = Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return new AgentStoredObject(normalizedRef, target.toString(), bytes);
    }

    @Override
    public AgentStoredObject put(String logicalRef, byte[] content, String contentType) throws IOException {
        return put(logicalRef, new ByteArrayInputStream(content), contentType);
    }

    @Override
    public Optional<byte[]> readIfExists(String logicalRef) throws IOException {
        var target = storage.resolve(normalize(logicalRef));
        if (!Files.exists(target)) {
            return Optional.empty();
        }
        return Optional.of(Files.readAllBytes(target));
    }

    @Override
    public byte[] read(String logicalRef) throws IOException {
        return Files.readAllBytes(storage.resolve(normalize(logicalRef)));
    }

    @Override
    public long size(String logicalRef) throws IOException {
        return Files.size(storage.resolve(normalize(logicalRef)));
    }

    @Override
    public void deleteIfExists(String logicalRef) throws IOException {
        Files.deleteIfExists(storage.resolve(normalize(logicalRef)));
    }
}
