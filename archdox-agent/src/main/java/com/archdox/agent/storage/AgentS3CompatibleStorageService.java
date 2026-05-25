package com.archdox.agent.storage;

import com.archdox.agent.cloud.ArchDoxAgentProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Optional;

public class AgentS3CompatibleStorageService implements AgentStorageService {
    private final String usage;
    private final AgentStorageTargetProfile profile;
    private final ArchDoxAgentProperties.S3Compatible connection;
    private final AgentS3ObjectGateway gateway;

    public AgentS3CompatibleStorageService(
            String usage,
            AgentStorageTargetProfile profile,
            ArchDoxAgentProperties.S3Compatible connection,
            AgentS3ObjectGateway gateway
    ) {
        this.usage = usage;
        this.profile = profile;
        this.connection = connection;
        this.gateway = gateway;
        if (profile.kind() != AgentStorageKind.S3_COMPATIBLE) {
            throw new IllegalArgumentException("S3-compatible storage service requires S3_COMPATIBLE profile");
        }
        if (profile.bucket() == null || profile.bucket().isBlank()) {
            throw new IllegalArgumentException("ArchDox Agent " + usage + " S3 bucket is required");
        }
    }

    @Override
    public AgentStorageKind kind() {
        return AgentStorageKind.S3_COMPATIBLE;
    }

    @Override
    public String normalize(String logicalRef) {
        return AgentStorageRefNormalizer.normalize(usage, logicalRef);
    }

    @Override
    public AgentStoredObject put(String logicalRef, InputStream input, String contentType) throws IOException {
        var normalizedRef = normalize(logicalRef);
        var temp = Files.createTempFile("archdox-agent-s3-", ".upload");
        try {
            try (input; var output = Files.newOutputStream(temp)) {
                input.transferTo(output);
            }
            var size = Files.size(temp);
            gateway.put(connection, profile.bucket(), objectKey(normalizedRef), safeContentType(contentType), temp, size);
            return new AgentStoredObject(normalizedRef, location(normalizedRef), size);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public AgentStoredObject put(String logicalRef, byte[] content, String contentType) throws IOException {
        return put(logicalRef, new ByteArrayInputStream(content), contentType);
    }

    @Override
    public Optional<byte[]> readIfExists(String logicalRef) throws IOException {
        return gateway.readIfExists(connection, profile.bucket(), objectKey(normalize(logicalRef)));
    }

    @Override
    public byte[] read(String logicalRef) throws IOException {
        return gateway.read(connection, profile.bucket(), objectKey(normalize(logicalRef)));
    }

    @Override
    public long size(String logicalRef) throws IOException {
        return gateway.size(connection, profile.bucket(), objectKey(normalize(logicalRef)));
    }

    @Override
    public void deleteIfExists(String logicalRef) throws IOException {
        gateway.deleteIfExists(connection, profile.bucket(), objectKey(normalize(logicalRef)));
    }

    String objectKey(String normalizedRef) {
        var prefix = normalizePrefix(profile.prefix());
        if (prefix == null) {
            return normalizedRef;
        }
        return prefix + "/" + normalizedRef;
    }

    private String location(String normalizedRef) {
        return "s3://" + profile.bucket() + "/" + objectKey(normalizedRef);
    }

    private String safeContentType(String contentType) {
        return contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        return AgentStorageRefNormalizer.normalize(usage + " prefix", prefix)
                .replaceFirst("/+$", "");
    }
}
