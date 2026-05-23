package com.archdox.cloud.photo.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.photo.application.PhotoService;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoUploadKind;
import com.archdox.cloud.photo.dto.ConfirmPhotoUploadRequest;
import com.archdox.cloud.photo.dto.CompletePhotoPickupRequest;
import com.archdox.cloud.photo.dto.CreatePhotoUploadIntentRequest;
import com.archdox.cloud.photo.dto.PhotoResponse;
import com.archdox.cloud.photo.dto.PhotoUploadIntentResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/photos")
public class PhotoController {
    private final PhotoService photoService;

    public PhotoController(PhotoService photoService) {
        this.photoService = photoService;
    }

    @PostMapping("/intent")
    @ResponseStatus(HttpStatus.CREATED)
    public PhotoUploadIntentResponse createIntent(
            @Valid @RequestBody CreatePhotoUploadIntentRequest request,
            Authentication authentication
    ) {
        return photoService.createIntent(request, (UserPrincipal) authentication.getPrincipal());
    }

    @GetMapping
    public List<PhotoResponse> list(@RequestParam Long reportId) {
        return photoService.listByReport(reportId);
    }

    @GetMapping("/{photoId}")
    public PhotoResponse get(@PathVariable Long photoId) {
        return photoService.get(photoId);
    }

    @GetMapping("/{photoId}/assets/{assetType}/content")
    public ResponseEntity<StreamingResponseBody> previewContent(
            @PathVariable Long photoId,
            @PathVariable PhotoAssetType assetType
    ) throws IOException {
        var content = photoService.preparePreviewContent(photoId, assetType);
        var builder = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(content.fileName())
                        .build()
                        .toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (content.bytes() != null && content.bytes() >= 0) {
            builder.contentLength(content.bytes());
        }
        return builder.body(content.body());
    }

    @PutMapping("/{photoId}/content/{kind}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadContent(
            @PathVariable Long photoId,
            @PathVariable PhotoUploadKind kind,
            HttpServletRequest request
    ) throws IOException {
        photoService.storeContent(photoId, kind, request.getContentLengthLong(), request.getInputStream());
    }

    @PostMapping("/{photoId}/confirm")
    public PhotoResponse confirm(
            @PathVariable Long photoId,
            @Valid @RequestBody ConfirmPhotoUploadRequest request,
            Authentication authentication
    ) {
        return photoService.confirm(photoId, request, (UserPrincipal) authentication.getPrincipal());
    }

    @PostMapping("/{photoId}/agent-pickup-complete")
    public PhotoResponse completeAgentPickup(
            @PathVariable Long photoId,
            @Valid @RequestBody CompletePhotoPickupRequest request
    ) throws IOException {
        return photoService.completeOriginalPickup(photoId, request);
    }
}
