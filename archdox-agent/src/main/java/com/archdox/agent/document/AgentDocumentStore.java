package com.archdox.agent.document;

import com.archdox.agent.cloud.ArchDoxAgentProperties;
import com.archdox.agent.storage.AgentStorageService;
import com.archdox.agent.storage.AgentStorageServiceFactory;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AgentDocumentStore {
    private final AgentStorageService storage;

    @Autowired
    public AgentDocumentStore(ArchDoxAgentProperties properties, AgentStorageServiceFactory storageServiceFactory) {
        this.storage = storageServiceFactory.documentArtifacts();
    }

    public AgentDocumentStore(ArchDoxAgentProperties properties) {
        this.storage = new AgentStorageServiceFactory(
                properties,
                new com.archdox.agent.storage.S3CompatibleAgentObjectGateway()).documentArtifacts();
    }

    AgentDocumentStore(AgentStorageService storage) {
        this.storage = storage;
    }

    public StoredDocument store(String logicalRef, byte[] content) throws IOException {
        var stored = storage.put(logicalRef, content, contentType(logicalRef));
        return new StoredDocument(stored.logicalRef(), stored.storageLocation(), stored.bytes());
    }

    public byte[] read(String logicalRef) throws IOException {
        return storage.read(logicalRef);
    }

    public long size(String logicalRef) throws IOException {
        return storage.size(logicalRef);
    }

    private String contentType(String logicalRef) {
        var ref = logicalRef == null ? "" : logicalRef.toLowerCase(java.util.Locale.ROOT);
        if (ref.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (ref.endsWith(".html") || ref.endsWith(".htm")) {
            return "text/html";
        }
        if (ref.endsWith(".pdf")) {
            return "application/pdf";
        }
        return "application/octet-stream";
    }

    public record StoredDocument(String logicalRef, String storageLocation, long bytes) {
    }
}
