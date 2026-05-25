package com.archdox.agent.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface AgentStorageService {
    AgentStorageKind kind();

    String normalize(String logicalRef);

    AgentStoredObject put(String logicalRef, InputStream input, String contentType) throws IOException;

    AgentStoredObject put(String logicalRef, byte[] content, String contentType) throws IOException;

    Optional<byte[]> readIfExists(String logicalRef) throws IOException;

    byte[] read(String logicalRef) throws IOException;

    long size(String logicalRef) throws IOException;

    void deleteIfExists(String logicalRef) throws IOException;
}
