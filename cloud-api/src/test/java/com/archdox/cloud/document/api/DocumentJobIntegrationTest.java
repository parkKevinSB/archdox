package com.archdox.cloud.document.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.archdox.cloud.document.event.DocumentGeneratedEvent;
import com.archdox.cloud.document.infra.DocumentJobRepository;
import com.archdox.cloud.document.infra.DocumentLocalObjectStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.parkkevinsb.bloom.EventBus;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DocumentJobIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("archdox.documents.generation.worker-interval-ms", () -> "10");
        registry.add("archdox.documents.generation.retry-base-delay-ms", () -> "10");
        registry.add("archdox.documents.storage.local-root", () -> "build/test-document-storage");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DocumentLocalObjectStore objectStore;

    @Autowired
    DocumentJobRepository documentJobRepository;

    @Autowired
    EventBus eventBus;

    @Test
    void createsCloudDocumentJobAndStoresDocxArtifact() throws Exception {
        var events = new ArrayList<DocumentGeneratedEvent>();
        eventBus.subscribe(DocumentGeneratedEvent.class, events::add);
        var user = signup();
        var templateOverride = createTemplateOverride(user);
        var projectId = createProject(user);
        var reportId = createReport(user, projectId);
        saveStep(user, reportId);
        saveChecklistStep(user, reportId);
        uploadWorkingPhoto(user, projectId, reportId);

        mockMvc.perform(post("/api/v1/inspection-reports/{reportId}/submit", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_TO_GENERATE"));

        var createResult = mockMvc.perform(post("/api/v1/inspection-reports/{reportId}/document-jobs", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.reportRevision").value(1))
                .andExpect(jsonPath("$.progressStep").value("QUEUED"))
                .andExpect(jsonPath("$.progressPercent").value(0))
                .andReturn();

        var created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        var jobId = created.get("id").asLong();
        var generated = waitForGenerated(user, jobId);
        assertTrue(generated.get("progressPercent").asInt() == 100);
        assertTrue("GENERATED".equals(generated.get("progressStep").asText()));
        assertSnapshotUsesTemplateOverride(jobId, templateOverride.revisionId(), templateOverride.storageRef());
        var artifactId = generated.get("artifacts").get(0).get("id").asLong();
        var storageRef = generated.get("artifacts").get(0).get("storageRef").asText();
        assertTrue(objectStore.exists(storageRef));
        assertTrue(events.stream().anyMatch(event -> event.documentJobId().equals(jobId)));

        mockMvc.perform(get("/api/v1/document-jobs/{jobId}", jobId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId))
                .andExpect(jsonPath("$.reportRevision").value(1))
                .andExpect(jsonPath("$.progressStep").value("GENERATED"))
                .andExpect(jsonPath("$.progressPercent").value(100))
                .andExpect(jsonPath("$.artifacts[0].storageRef").value(storageRef));

        mockMvc.perform(get("/api/v1/inspection-reports/{reportId}", reportId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GENERATED"))
                .andExpect(jsonPath("$.contentRevision").value(1))
                .andExpect(jsonPath("$.submittedRevision").value(1))
                .andExpect(jsonPath("$.generatedRevision").value(1))
                .andExpect(jsonPath("$.lastDocumentJobId").value(jobId));

        var deliveryResult = mockMvc.perform(post("/api/v1/document-jobs/{jobId}/delivery-requests", jobId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "artifactId": %d,
                                  "channel": "DOWNLOAD"
                                }
                                """.formatted(artifactId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.artifactId").value(artifactId))
                .andExpect(jsonPath("$.downloadUrl").value("/api/v1/document-artifacts/" + artifactId + "/download"))
                .andReturn();
        var deliveryId = readId(deliveryResult.getResponse().getContentAsString());

        mockMvc.perform(get("/api/v1/document-delivery-requests/{deliveryId}", deliveryId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(deliveryId))
                .andExpect(jsonPath("$.downloadUrl").value("/api/v1/document-artifacts/" + artifactId + "/download"));

        mockMvc.perform(get("/api/v1/document-jobs/{jobId}/delivery-requests", jobId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(deliveryId));

        mockMvc.perform(get("/api/v1/document-artifacts/{artifactId}/download", artifactId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk());
        byte[] content;
        try (var input = objectStore.open(storageRef)) {
            content = input.readAllBytes();
        }
        assertTrue(content.length > 0);
        assertTrue(content[0] == 'P' && content[1] == 'K');
        var documentXml = docxText(content);
        assertTrue(documentXml.contains("Report title: Daily supervision report"));
        assertTrue(documentXml.contains("Weather: Clear"));
        assertTrue(documentXml.contains("Template: DAILY_TEMPLATE v1"));
    }

    private TemplateOverride createTemplateOverride(TestUser user) throws Exception {
        var templateResult = mockMvc.perform(post("/api/v1/config/document-templates")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "DAILY_TEMPLATE",
                                  "name": "Daily Template",
                                  "reportType": "DAILY_SUPERVISION"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        var templateId = readId(templateResult.getResponse().getContentAsString());
        var revisionResult = mockMvc.perform(post("/api/v1/config/document-templates/{templateId}/revisions", templateId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": {
                                    "required": ["projectName"]
                                  },
                                  "composePolicy": {
                                    "photoSection": "photoTable"
                                  },
                                  "aiPrompts": {}
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        var revisionId = readId(revisionResult.getResponse().getContentAsString());
        var file = new MockMultipartFile(
                "file",
                "daily-template.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                templateDocx("""
                        Report title: ${report.title}
                        Weather: ${weather}
                        Template: ${templateCode} v${templateVersion}
                        """));
        var uploadResult = mockMvc.perform(multipart("/api/v1/config/document-template-revisions/{revisionId}/content", revisionId)
                        .file(file)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateStorageKind").value("API_LOCAL"))
                .andReturn();
        var uploaded = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        var templateStorageRef = uploaded.get("templateStorageRef").asText();
        assertTrue(templateStorageRef.startsWith("templates/offices/" + user.officeId() + "/document-templates/"));
        assertTrue(objectStore.exists(templateStorageRef));

        var templateDownloadResult = mockMvc.perform(get("/api/v1/config/document-template-revisions/{revisionId}/content", revisionId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andReturn();
        var templateContent = templateDownloadResult.getResponse().getContentAsByteArray();
        assertTrue(templateContent[0] == 'P' && templateContent[1] == 'K');

        mockMvc.perform(post("/api/v1/config/document-template-revisions/{revisionId}/publish", revisionId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mockMvc.perform(put("/api/v1/config/office-overrides/{reportType}", "DAILY_SUPERVISION")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateRevisionId": %d
                                }
                        """.formatted(revisionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template.revisionId").value(revisionId));
        return new TemplateOverride(revisionId, templateStorageRef);
    }

    @SuppressWarnings("unchecked")
    private void assertSnapshotUsesTemplateOverride(long jobId, long templateRevisionId, String templateStorageRef) {
        var job = documentJobRepository.findById(jobId).orElseThrow();
        var configuration = (Map<String, Object>) job.inputSnapshotJson().get("configuration");
        var template = (Map<String, Object>) configuration.get("template");
        assertTrue("OFFICE_OVERRIDE".equals(template.get("source")));
        assertTrue(templateRevisionId == ((Number) template.get("revisionId")).longValue());
        assertTrue("DAILY_TEMPLATE".equals(template.get("code")));
        assertTrue(templateStorageRef.equals(template.get("storageRef")));
    }

    private String docxText(byte[] content) throws Exception {
        try (var input = new ZipInputStream(new java.io.ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                if (!"word/document.xml".equals(entry.getName())) {
                    continue;
                }
                var output = new ByteArrayOutputStream();
                input.transferTo(output);
                return output.toString(StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private byte[] templateDocx(String bodyText) throws Exception {
        try (var output = new ByteArrayOutputStream();
             var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            putZipEntry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            putZipEntry(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """);
            putZipEntry(zip, "word/document.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body>
                        <w:p><w:r><w:t>%s</w:t></w:r></w:p>
                        <w:sectPr/>
                      </w:body>
                    </w:document>
                    """.formatted(escapeXml(bodyText)));
            zip.finish();
            return output.toByteArray();
        }
    }

    private void putZipEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private JsonNode waitForGenerated(TestUser user, long jobId) throws Exception {
        for (int i = 0; i < 80; i++) {
            var result = mockMvc.perform(get("/api/v1/document-jobs/{jobId}", jobId)
                            .header("Authorization", bearer(user.accessToken()))
                            .header("X-Office-Id", user.officeId()))
                    .andExpect(status().isOk())
                    .andReturn();
            var node = objectMapper.readTree(result.getResponse().getContentAsString());
            if ("GENERATED".equals(node.get("status").asText())) {
                return node;
            }
            Thread.sleep(50);
        }
        fail("Document job did not reach GENERATED status");
        return null;
    }

    private TestUser signup() throws Exception {
        var signupResult = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "document-user@example.com",
                                  "password": "password-1234",
                                  "name": "Document User"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        var accessToken = objectMapper.readTree(signupResult.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
        var meResult = mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andReturn();
        var me = objectMapper.readTree(meResult.getResponse().getContentAsString());
        return new TestUser(me.get("offices").get(0).get("id").asLong(), accessToken);
    }

    private long createProject(TestUser user) throws Exception {
        var result = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Document Tower\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private long createReport(TestUser user, long projectId) throws Exception {
        var result = mockMvc.perform(post("/api/v1/inspection-reports")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "reportType": "DAILY_SUPERVISION",
                                  "title": "Daily supervision report"
                                }
                                """.formatted(projectId)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private void saveStep(TestUser user, long reportId) throws Exception {
        mockMvc.perform(put("/api/v1/inspection-reports/{reportId}/steps/{stepCode}", reportId, "BASIC_INFO")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payload": {
                                    "inspectionDate": "2026-05-23",
                                    "inspectorName": "Document Job",
                                    "weather": "Clear",
                                    "location": "Site A"
                                  }
                                }
                                """))
                .andExpect(status().isOk());
    }

    private void saveChecklistStep(TestUser user, long reportId) throws Exception {
        mockMvc.perform(put("/api/v1/inspection-reports/{reportId}/steps/{stepCode}", reportId, "CHECKLIST")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payload": {
                                    "checklistSummary": "Checked",
                                    "issueCount": 0
                                  }
                                }
                                """))
                .andExpect(status().isOk());
    }

    private void uploadWorkingPhoto(TestUser user, long projectId, long reportId) throws Exception {
        var bytes = "document-job-working-photo".getBytes(StandardCharsets.UTF_8);
        var hash = sha256(bytes);
        var intentResult = mockMvc.perform(post("/api/v1/photos/intent")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId": %d,
                                  "reportId": %d,
                                  "stepCode": "PHOTOS",
                                  "captureKind": "UPLOAD",
                                  "mime": "image/png",
                                  "bytes": %d,
                                  "hash": "%s",
                                  "width": 640,
                                  "height": 480,
                                  "wantsOriginal": false
                                }
                                """.formatted(projectId, reportId, bytes.length, hash)))
                .andExpect(status().isCreated())
                .andReturn();
        var photoId = objectMapper.readTree(intentResult.getResponse().getContentAsString()).get("photoId").asLong();
        mockMvc.perform(put("/api/v1/photos/{photoId}/content/{kind}", photoId, "WORKING")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.IMAGE_PNG)
                        .content(bytes))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/photos/{photoId}/confirm", photoId)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "hash": "%s",
                                  "bytes": %d,
                                  "width": 640,
                                  "height": 480
                                }
                                """.formatted(hash, bytes.length)))
                .andExpect(status().isOk());
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private long readId(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record TestUser(long officeId, String accessToken) {
    }

    private record TemplateOverride(long revisionId, String storageRef) {
    }
}
