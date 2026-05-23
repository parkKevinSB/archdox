package com.archdox.agent.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.archdox.document.SimpleDocumentEngine;
import com.archdox.agent.document.AgentDocumentStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentRenderCommandExecutorTest {
    @Test
    void rendersDocumentAndStoresArtifactMetadata(@TempDir Path tempDir) throws Exception {
        var properties = new ArchDoxAgentProperties();
        properties.setLocalStorageRoot(tempDir.toString());
        var executor = new DocumentRenderCommandExecutor(
                new SimpleDocumentEngine(),
                new AgentDocumentStore(properties));
        var payload = Map.<String, Object>of(
                "documentJobId", 700L,
                "officeId", 10L,
                "reportId", 1000L,
                "outputFormat", "DOCX",
                "inputSnapshot", Map.of("report", Map.of("title", "Report")),
                "template", Map.of(
                        "templateCode", "INSPECTION_REPORT",
                        "version", 1,
                        "storageRef", "templates/default.docx",
                        "schemaJson", "{}",
                        "composePolicyJson", "{}"),
                "photos", List.of(),
                "resultStorageKind", "ARCHDOX_AGENT");

        var result = executor.execute(new CloudInboundMessage(
                "COMMAND",
                null,
                null,
                null,
                99L,
                "GENERATE_DOCUMENT",
                payload,
                null));

        @SuppressWarnings("unchecked")
        var artifacts = (List<Map<String, Object>>) result.get("artifacts");
        assertEquals(1, artifacts.size());
        assertEquals("DOCX", artifacts.get(0).get("artifactType"));
        assertEquals("ARCHDOX_AGENT", artifacts.get(0).get("storageKind"));
        assertTrue(Files.exists(tempDir.resolve(String.valueOf(artifacts.get(0).get("storageRef")))));
    }
}
