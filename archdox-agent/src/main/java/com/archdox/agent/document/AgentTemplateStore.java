package com.archdox.agent.document;

import com.archdox.agent.cloud.ArchDoxAgentProperties;
import com.archdox.agent.storage.AgentStorageService;
import com.archdox.agent.storage.AgentStorageServiceFactory;
import java.io.IOException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AgentTemplateStore {
    private final AgentStorageService storage;

    @Autowired
    public AgentTemplateStore(ArchDoxAgentProperties properties, AgentStorageServiceFactory storageServiceFactory) {
        this.storage = storageServiceFactory.templateCache();
    }

    public AgentTemplateStore(ArchDoxAgentProperties properties) {
        this.storage = new AgentStorageServiceFactory(
                properties,
                new com.archdox.agent.storage.S3CompatibleAgentObjectGateway()).templateCache();
    }

    public Optional<byte[]> readIfExists(String logicalRef) throws IOException {
        if (logicalRef == null || logicalRef.isBlank()) {
            return Optional.empty();
        }
        return storage.readIfExists(logicalRef);
    }

    public void write(String logicalRef, byte[] content) throws IOException {
        if (logicalRef == null || logicalRef.isBlank() || content == null || content.length == 0) {
            return;
        }
        storage.put(logicalRef, content, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }
}
