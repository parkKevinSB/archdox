package com.archdox.agent.storage;

import com.archdox.agent.cloud.ArchDoxAgentProperties;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public interface AgentS3ObjectGateway {
    void put(
            ArchDoxAgentProperties.S3Compatible connection,
            String bucket,
            String objectKey,
            String contentType,
            Path file,
            long size
    ) throws IOException;

    Optional<byte[]> readIfExists(
            ArchDoxAgentProperties.S3Compatible connection,
            String bucket,
            String objectKey
    ) throws IOException;

    byte[] read(
            ArchDoxAgentProperties.S3Compatible connection,
            String bucket,
            String objectKey
    ) throws IOException;

    long size(
            ArchDoxAgentProperties.S3Compatible connection,
            String bucket,
            String objectKey
    ) throws IOException;

    void deleteIfExists(
            ArchDoxAgentProperties.S3Compatible connection,
            String bucket,
            String objectKey
    ) throws IOException;
}
