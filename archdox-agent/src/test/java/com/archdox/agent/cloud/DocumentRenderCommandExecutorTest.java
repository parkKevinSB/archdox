package com.archdox.agent.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.archdox.document.DocxTemplateDocumentEngine;
import com.archdox.document.SimpleDocumentEngine;
import com.archdox.agent.document.AgentDocumentStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentRenderCommandExecutorTest {
    @Test
    void rendersDocumentAndStoresArtifactMetadata(@TempDir Path tempDir) throws Exception {
        var properties = new ArchDoxAgentProperties();
        properties.setLocalStorageRoot(tempDir.toString());
        var template = templateDocx("Project: ${projectName}");
        var executor = new DocumentRenderCommandExecutor(
                new DocxTemplateDocumentEngine(spec -> Optional.of(template), new SimpleDocumentEngine()),
                new AgentDocumentStore(properties));
        var payload = Map.<String, Object>of(
                "documentJobId", 700L,
                "officeId", 10L,
                "reportId", 1000L,
                "outputFormat", "DOCX",
                "inputSnapshot", Map.of(
                        "report", Map.of("title", "Report"),
                        "templateFields", Map.of("projectName", "Agent template project")),
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
        var artifactPath = tempDir.resolve(String.valueOf(artifacts.get(0).get("storageRef")));
        assertTrue(Files.exists(artifactPath));
        assertTrue(documentXml(Files.readAllBytes(artifactPath)).contains("Project: Agent template project"));
    }

    private byte[] templateDocx(String bodyText) throws Exception {
        try (var output = new ByteArrayOutputStream();
             var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """);
            put(zip, "word/document.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p><w:r><w:t>%s</w:t></w:r></w:p>
                        <w:sectPr/>
                      </w:body>
                    </w:document>
                    """.formatted(bodyText));
            zip.finish();
            return output.toByteArray();
        }
    }

    private void put(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String documentXml(byte[] docx) throws Exception {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(docx), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if ("word/document.xml".equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return "";
    }
}
