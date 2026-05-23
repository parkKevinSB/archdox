package com.archdox.cloud.agent.api;

import com.archdox.cloud.agent.application.ArchDoxAgentAuthenticationService;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import com.archdox.cloud.photo.infra.PhotoLocalObjectStore;
import com.archdox.cloud.photo.infra.PhotoRepository;
import java.io.IOException;
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
    private final PhotoLocalObjectStore objectStore;

    public AgentPhotoContentController(
            ArchDoxAgentAuthenticationService authenticationService,
            PhotoRepository photoRepository,
            PhotoAssetRepository photoAssetRepository,
            PhotoLocalObjectStore objectStore
    ) {
        this.authenticationService = authenticationService;
        this.photoRepository = photoRepository;
        this.photoAssetRepository = photoAssetRepository;
        this.objectStore = objectStore;
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
        if (asset.storageKind() != PhotoStorageKind.API_LOCAL) {
            throw new BadRequestException("Agent API content download supports API_LOCAL storage only");
        }
        var body = (StreamingResponseBody) outputStream -> objectStore.copyTo(asset.storageRef(), outputStream);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.mimeType()))
                .contentLength(objectStore.size(asset.storageRef()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
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
