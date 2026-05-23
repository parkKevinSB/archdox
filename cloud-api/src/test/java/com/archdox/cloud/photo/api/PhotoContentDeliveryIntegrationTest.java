package com.archdox.cloud.photo.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PhotoContentDeliveryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("archdox.photos.storage.upload-target", () -> "API_LOCAL");
        registry.add("archdox.photos.storage.local-root", () -> "build/test-photo-preview-storage");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void userCanPreviewWorkingAndThumbnailButNotOriginal() throws Exception {
        var user = signup();
        var projectId = createProject(user);
        var reportId = createReport(user, projectId);
        var workingBytes = "working-image-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var thumbnailBytes = "thumbnail-image-content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var hash = sha256(workingBytes);

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
                                  "wantsOriginal": true
                                }
                                """.formatted(projectId, reportId, workingBytes.length, hash)))
                .andExpect(status().isCreated())
                .andReturn();
        var photoId = objectMapper.readTree(intentResult.getResponse().getContentAsString())
                .get("photoId")
                .asLong();

        uploadAsset(user, photoId, "ORIGINAL", workingBytes, MediaType.IMAGE_PNG);
        uploadAsset(user, photoId, "WORKING", workingBytes, MediaType.IMAGE_PNG);
        uploadAsset(user, photoId, "THUMBNAIL", thumbnailBytes, MediaType.parseMediaType("image/webp"));

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
                                """.formatted(hash, workingBytes.length)))
                .andExpect(status().isOk());

        var workingResult = mockMvc.perform(get("/api/v1/photos/{photoId}/assets/{assetType}/content", photoId, "WORKING")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();
        assertArrayEquals(workingBytes, workingResult.getResponse().getContentAsByteArray());

        var thumbnailResult = mockMvc.perform(get("/api/v1/photos/{photoId}/assets/{assetType}/content", photoId, "THUMBNAIL")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/webp"))
                .andReturn();
        assertArrayEquals(thumbnailBytes, thumbnailResult.getResponse().getContentAsByteArray());

        mockMvc.perform(get("/api/v1/photos/{photoId}/assets/{assetType}/content", photoId, "ORIGINAL")
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId()))
                .andExpect(status().isBadRequest());
    }

    private void uploadAsset(TestUser user, long photoId, String assetType, byte[] content, MediaType mediaType) throws Exception {
        mockMvc.perform(put("/api/v1/photos/{photoId}/content/{kind}", photoId, assetType)
                        .header("Authorization", bearer(user.accessToken()))
                        .header("X-Office-Id", user.officeId())
                        .contentType(mediaType)
                        .content(content))
                .andExpect(status().isNoContent());
    }

    private TestUser signup() throws Exception {
        var signupResult = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "photo-preview@example.com",
                                  "password": "password-1234",
                                  "name": "Photo Preview User"
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
                        .content("{\"name\":\"Photo Preview Project\"}"))
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
                                  "title": "Photo preview report"
                                }
                                """.formatted(projectId)))
                .andExpect(status().isCreated())
                .andReturn();
        return readId(result.getResponse().getContentAsString());
    }

    private long readId(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("id").asLong();
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record TestUser(long officeId, String accessToken) {
    }
}
