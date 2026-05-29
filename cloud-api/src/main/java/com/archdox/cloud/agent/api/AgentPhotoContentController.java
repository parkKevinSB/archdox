package com.archdox.cloud.agent.api;

import com.archdox.cloud.agent.application.ArchDoxAgentAuthenticationService;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.photo.application.PhotoStorageAdapterResolver;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import com.archdox.cloud.photo.infra.PhotoRepository;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/agent/api/v1/photos")
public class AgentPhotoContentController {
    private final ArchDoxAgentAuthenticationService authenticationService;
    private final PhotoRepository photoRepository;
    private final PhotoAssetRepository photoAssetRepository;
    private final PhotoStorageAdapterResolver storageAdapterResolver;

    public AgentPhotoContentController(
            ArchDoxAgentAuthenticationService authenticationService,
            PhotoRepository photoRepository,
            PhotoAssetRepository photoAssetRepository,
            PhotoStorageAdapterResolver storageAdapterResolver
    ) {
        this.authenticationService = authenticationService;
        this.photoRepository = photoRepository;
        this.photoAssetRepository = photoAssetRepository;
        this.storageAdapterResolver = storageAdapterResolver;
    }

    @GetMapping("/{photoId}/assets/{assetType}/content")
    public ResponseEntity<StreamingResponseBody> downloadPhotoAsset(
            @PathVariable Long photoId,
            @PathVariable PhotoAssetType assetType,
            @RequestHeader(name = "X-Agent-Token", required = false) String token,
            @RequestHeader(name = "X-Agent-Id", required = false) Long agentId,
            @RequestHeader(name = "X-Agent-Device-Secret", required = false) String deviceSecret,
            @RequestHeader(name = "X-Agent-Office-Id", required = false) String officeHeader
    ) throws IOException {
        var officeId = parseOfficeId(officeHeader);
        authenticationService.authenticateDownload(agentId, deviceSecret, token, officeId);
        var photo = photoRepository.findByIdAndOfficeId(photoId, officeId)
                .orElseThrow(() -> new NotFoundException("Photo not found"));
        var asset = photoAssetRepository.findByPhotoIdAndAssetType(photo.id(), assetType)
                .orElseThrow(() -> new NotFoundException("Photo asset not found"));
        if (asset.storageKind() == PhotoStorageKind.AGENT_MANAGED || asset.storageKind() == PhotoStorageKind.DELETED) {
            throw new BadRequestException("Photo asset content is not available through Cloud");
        }
        var input = openAssetContent(asset);
        var body = (StreamingResponseBody) outputStream -> {
            try (input) {
                input.transferTo(outputStream);
            }
        };
        var builder = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.mimeType()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline");
        if (asset.bytes() != null && asset.bytes() >= 0) {
            builder.contentLength(asset.bytes());
        }
        return builder.body(body);
    }

    private InputStream openAssetContent(com.archdox.cloud.photo.domain.PhotoAsset asset) throws IOException {
        try {
            return storageAdapterResolver.forStorageKind(asset.storageKind()).openContent(asset);
        } catch (FileNotFoundException | NoSuchFileException ex) {
            throw new NotFoundException("Photo asset content not found");
        }
    }

    private Long parseOfficeId(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("X-Agent-Office-Id is required");
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Invalid X-Agent-Office-Id");
        }
    }
}
