package com.archdox.cloud.photo.application;

import com.archdox.cloud.checklist.application.ChecklistService;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.ConflictException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.application.InspectionReportService;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.photo.domain.Photo;
import com.archdox.cloud.photo.domain.PhotoAsset;
import com.archdox.cloud.photo.domain.PhotoAssetStatus;
import com.archdox.cloud.photo.domain.PhotoAssetType;
import com.archdox.cloud.photo.domain.PhotoCaptureKind;
import com.archdox.cloud.photo.domain.PhotoPickupStatus;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.domain.PhotoStorageKind;
import com.archdox.cloud.photo.domain.PhotoUploadKind;
import com.archdox.cloud.photo.domain.PhotoUploadTarget;
import com.archdox.cloud.photo.dto.ConfirmPhotoUploadRequest;
import com.archdox.cloud.photo.dto.CompletePhotoPickupRequest;
import com.archdox.cloud.photo.dto.CreatePhotoUploadIntentRequest;
import com.archdox.cloud.photo.dto.PhotoAssetResponse;
import com.archdox.cloud.photo.dto.PhotoResponse;
import com.archdox.cloud.photo.dto.PhotoUploadInstructionResponse;
import com.archdox.cloud.photo.dto.PhotoUploadIntentResponse;
import com.archdox.cloud.photo.event.PhotoUploadConfirmed;
import com.archdox.cloud.photo.infra.PhotoAssetRepository;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.cloud.project.application.ProjectService;
import io.github.parkkevinsb.bloom.EventBus;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PhotoService {
    private static final int UPLOAD_TTL_MINUTES = 10;

    private final PhotoRepository photoRepository;
    private final PhotoAssetRepository photoAssetRepository;
    private final ProjectService projectService;
    private final InspectionReportService inspectionReportService;
    private final ChecklistService checklistService;
    private final PhotoStorageRefFactory storageRefFactory;
    private final PhotoStorageAdapterResolver storageAdapterResolver;
    private final EventBus eventBus;

    public PhotoService(
            PhotoRepository photoRepository,
            PhotoAssetRepository photoAssetRepository,
            ProjectService projectService,
            InspectionReportService inspectionReportService,
            ChecklistService checklistService,
            PhotoStorageRefFactory storageRefFactory,
            PhotoStorageAdapterResolver storageAdapterResolver,
            EventBus eventBus
    ) {
        this.photoRepository = photoRepository;
        this.photoAssetRepository = photoAssetRepository;
        this.projectService = projectService;
        this.inspectionReportService = inspectionReportService;
        this.checklistService = checklistService;
        this.storageRefFactory = storageRefFactory;
        this.storageAdapterResolver = storageAdapterResolver;
        this.eventBus = eventBus;
    }

    @Transactional
    public PhotoUploadIntentResponse createIntent(CreatePhotoUploadIntentRequest request, UserPrincipal principal) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var hash = normalizeHash(request.hash());
        var resolved = resolveParent(request);
        validateChecklistLink(request, resolved);
        var existing = photoRepository.findFirstByOfficeIdAndHashSha256AndStatus(
                officeId,
                hash,
                PhotoStatus.UPLOADED);
        if (existing.isPresent() && isSameUploadContext(existing.get(), resolved, request)) {
            var photo = existing.get();
            return new PhotoUploadIntentResponse(
                    photo.id(),
                    photo.uploadTarget(),
                    false,
                    List.of(),
                    null,
                    null,
                    toResponse(photo));
        }
        var now = OffsetDateTime.now();
        var mimeType = normalizeMime(request.mime());
        var refs = storageRefFactory.create(officeId, resolved.projectId(), resolved.reportId(), mimeType);
        var includeOriginal = request.wantsOriginal() == null || request.wantsOriginal();
        var uploadTarget = storageAdapterResolver.configuredUploadTarget();
        var adapter = storageAdapterResolver.forTarget(uploadTarget);
        var primaryStorageKind = adapter.storageKindFor(uploadTarget, PhotoAssetType.WORKING);
        var photo = new Photo(
                officeId,
                resolved.projectId(),
                resolved.reportId(),
                normalizeStepCode(request.stepCode()),
                request.checklistItemId(),
                request.captureKind() == null ? PhotoCaptureKind.UPLOAD : request.captureKind(),
                mimeType,
                request.bytes(),
                hash,
                primaryStorageKind,
                refs.workingRef(),
                refs.thumbnailRef(),
                uploadTarget,
                principal.userId(),
                request.takenAt(),
                request.gpsLat(),
                request.gpsLng(),
                now);
        var saved = photoRepository.save(photo);
        if (!includeOriginal) {
            saved.markOriginalPickupNotRequired(now);
        }
        var assets = createAssets(saved, refs, mimeType, request.bytes(), hash, includeOriginal, uploadTarget, adapter, now);
        photoAssetRepository.saveAll(assets);
        var expiresAt = now.plusMinutes(UPLOAD_TTL_MINUTES);
        return new PhotoUploadIntentResponse(
                saved.id(),
                saved.uploadTarget(),
                true,
                adapter.createUploadInstructions(saved, assets, expiresAt),
                null,
                expiresAt,
                toResponse(saved));
    }

    @Transactional(readOnly = true)
    public List<PhotoResponse> listByReport(Long reportId) {
        var report = inspectionReportService.requireReport(reportId);
        var officeId = OfficeContext.requireCurrentOfficeId();
        return photoRepository.findByOfficeIdAndReportIdOrderByIdDesc(officeId, report.id()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PhotoResponse get(Long photoId) {
        return toResponse(requirePhoto(photoId));
    }

    @Transactional(readOnly = true)
    public PhotoAssetContent preparePreviewContent(Long photoId, PhotoAssetType assetType) throws IOException {
        var photo = requirePhoto(photoId);
        if (assetType == PhotoAssetType.ORIGINAL) {
            throw new BadRequestException("Original photo content is not exposed through the user preview API");
        }
        var asset = requireAsset(photo, assetType);
        if (asset.status() != PhotoAssetStatus.UPLOADED) {
            throw new ConflictException("Photo asset is not ready for preview: " + asset.status());
        }
        if (asset.storageKind() == PhotoStorageKind.AGENT_MANAGED) {
            throw new ConflictException("Agent-managed photo content is not available through Cloud preview");
        }
        var input = storageAdapterResolver.forStorageKind(asset.storageKind()).openContent(asset);
        return new PhotoAssetContent(
                previewFileName(photo.id(), assetType, asset.mimeType()),
                asset.mimeType(),
                asset.bytes(),
                outputStream -> {
                    try (input) {
                        input.transferTo(outputStream);
                    }
                });
    }

    @Transactional
    public PhotoResponse confirm(Long photoId, ConfirmPhotoUploadRequest request, UserPrincipal principal) {
        var photo = requirePhoto(photoId);
        if (photo.status() != PhotoStatus.PENDING_UPLOAD) {
            throw new BadRequestException("Photo cannot be confirmed in status " + photo.status());
        }
        if (!photo.hashSha256().equals(normalizeHash(request.hash()))) {
            throw new BadRequestException("Photo hash does not match upload intent");
        }
        var workingAsset = requireAsset(photo, PhotoAssetType.WORKING);
        if (photo.originalPickupStatus() != PhotoPickupStatus.NOT_REQUIRED) {
            var originalAsset = requireAsset(photo, PhotoAssetType.ORIGINAL);
            if (originalAsset.storageKind() == PhotoStorageKind.API_LOCAL
                    && originalAsset.status() != PhotoAssetStatus.UPLOADED) {
                throw new BadRequestException("Original photo content must be uploaded before confirm");
            }
            if (originalAsset.status() == PhotoAssetStatus.PENDING_UPLOAD) {
                originalAsset.markUploaded(originalAsset.bytes(), OffsetDateTime.now());
            }
        } else {
            if (workingAsset.storageKind() == PhotoStorageKind.API_LOCAL && workingAsset.status() != PhotoAssetStatus.UPLOADED) {
                throw new BadRequestException("Working photo content must be uploaded before confirm when original is not uploaded");
            }
            if (workingAsset.status() == PhotoAssetStatus.PENDING_UPLOAD) {
                workingAsset.markUploaded(request.bytes(), OffsetDateTime.now());
                workingAsset.updateImageInfo(request.bytes(), request.width(), request.height(), normalizeHash(request.hash()));
            }
        }
        var confirmedAt = OffsetDateTime.now();
        photo.confirm(request.bytes(), request.width(), request.height(), principal.userId(), confirmedAt);
        publishAfterCommit(new PhotoUploadConfirmed(
                photo.officeId(),
                photo.id(),
                photo.reportId(),
                photo.projectId(),
                confirmedAt));
        return toResponse(photo);
    }

    @Transactional
    public void storeContent(Long photoId, PhotoUploadKind kind, Long contentLength, InputStream input) throws IOException {
        var photo = requirePhoto(photoId);
        if (photo.status() != PhotoStatus.PENDING_UPLOAD) {
            throw new BadRequestException("Photo content cannot be uploaded in status " + photo.status());
        }
        var asset = requireAsset(photo, assetType(kind));
        if (asset.storageKind() != PhotoStorageKind.API_LOCAL) {
            throw new BadRequestException("Use the returned upload URL for storage kind " + asset.storageKind());
        }
        storageAdapterResolver.forStorageKind(asset.storageKind()).storeContent(asset, contentLength, input);
        asset.markUploaded(contentLength == null || contentLength < 0 ? null : contentLength, OffsetDateTime.now());
    }

    @Transactional
    public PhotoResponse completeOriginalPickup(
            Long photoId,
            CompletePhotoPickupRequest request
    ) throws IOException {
        var photo = requirePhoto(photoId);
        var originalAsset = requireAsset(photo, PhotoAssetType.ORIGINAL);
        if (originalAsset.status() != PhotoAssetStatus.UPLOADED) {
            throw new BadRequestException("Original photo must be uploaded before pickup completion");
        }
        var now = OffsetDateTime.now();
        var temporaryRef = originalAsset.storageRef();
        if (Boolean.TRUE.equals(request.deleteTemporaryOriginal()) && originalAsset.temporary()) {
            storageAdapterResolver.forStorageKind(originalAsset.storageKind()).deleteIfExists(temporaryRef);
            photo.markOriginalTemporaryDeleted(now);
        }
        originalAsset.relocateToAgentManaged(request.agentOriginalStorageRef().trim(), now);
        photo.markOriginalPickedUp(now);
        return toResponse(photo);
    }

    private ParentRef resolveParent(CreatePhotoUploadIntentRequest request) {
        if (request.reportId() != null) {
            InspectionReport report = inspectionReportService.requireReport(request.reportId());
            if (request.projectId() != null && !request.projectId().equals(report.projectId())) {
                throw new BadRequestException("projectId does not match the report's project");
            }
            return new ParentRef(report.projectId(), report.id(), report);
        }
        if (request.projectId() == null) {
            throw new BadRequestException("projectId or reportId is required");
        }
        var project = projectService.requireProject(request.projectId());
        return new ParentRef(project.id(), null, null);
    }

    private void validateChecklistLink(CreatePhotoUploadIntentRequest request, ParentRef resolved) {
        if (request.checklistItemId() == null) {
            return;
        }
        if (resolved.report() == null) {
            throw new BadRequestException("checklistItemId requires reportId");
        }
        checklistService.requireItemForReport(resolved.report(), request.checklistItemId());
    }

    private boolean isSameUploadContext(Photo photo, ParentRef resolved, CreatePhotoUploadIntentRequest request) {
        return Objects.equals(photo.projectId(), resolved.projectId())
                && Objects.equals(photo.reportId(), resolved.reportId())
                && Objects.equals(photo.checklistItemId(), request.checklistItemId())
                && Objects.equals(photo.stepCode(), normalizeStepCode(request.stepCode()));
    }

    private Photo requirePhoto(Long photoId) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        return photoRepository.findByIdAndOfficeId(photoId, officeId)
                .orElseThrow(() -> new NotFoundException("Photo not found"));
    }

    private List<PhotoAsset> createAssets(
            Photo photo,
            PhotoStorageRefs refs,
            String mimeType,
            Long bytes,
            String hash,
            boolean includeOriginal,
            PhotoUploadTarget uploadTarget,
            PhotoStorageAdapter adapter,
            OffsetDateTime now
    ) {
        var assets = new java.util.ArrayList<PhotoAsset>();
        if (includeOriginal) {
            assets.add(new PhotoAsset(
                    photo,
                    PhotoAssetType.ORIGINAL,
                    adapter.storageKindFor(uploadTarget, PhotoAssetType.ORIGINAL),
                    refs.originalRef(),
                    mimeType,
                    bytes,
                    hash,
                    true,
                    now));
        }
        assets.add(new PhotoAsset(
                photo,
                PhotoAssetType.WORKING,
                adapter.storageKindFor(uploadTarget, PhotoAssetType.WORKING),
                refs.workingRef(),
                mimeType,
                bytes,
                hash,
                false,
                now));
        assets.add(new PhotoAsset(
                photo,
                PhotoAssetType.THUMBNAIL,
                adapter.storageKindFor(uploadTarget, PhotoAssetType.THUMBNAIL),
                refs.thumbnailRef(),
                "image/webp",
                null,
                null,
                false,
                now));
        return assets;
    }

    private PhotoResponse toResponse(Photo photo) {
        var assets = photoAssetRepository.findByPhotoIdOrderById(photo.id()).stream()
                .map(this::toAssetResponse)
                .toList();
        return new PhotoResponse(
                photo.id(),
                photo.officeId(),
                photo.projectId(),
                photo.reportId(),
                photo.stepCode(),
                photo.checklistItemId(),
                photo.captureKind(),
                photo.status(),
                photo.mimeType(),
                photo.width(),
                photo.height(),
                photo.bytes(),
                photo.hashSha256(),
                photo.storageKind(),
                photo.storageRef(),
                photo.thumbnailStorageRef(),
                photo.uploadTarget(),
                photo.originalPickupStatus(),
                photo.originalPickedUpAt(),
                photo.originalTemporaryDeletedAt(),
                assets);
    }

    private PhotoAssetResponse toAssetResponse(PhotoAsset asset) {
        return new PhotoAssetResponse(
                asset.assetType(),
                asset.status(),
                asset.storageKind(),
                asset.storageRef(),
                asset.mimeType(),
                asset.bytes(),
                asset.width(),
                asset.height(),
                asset.hashSha256(),
                asset.temporary());
    }

    private PhotoAsset requireAsset(Photo photo, PhotoAssetType assetType) {
        return photoAssetRepository.findByPhotoIdAndAssetType(photo.id(), assetType)
                .orElseThrow(() -> new NotFoundException("Photo asset not found"));
    }

    private PhotoAssetType assetType(PhotoUploadKind kind) {
        return switch (kind) {
            case ORIGINAL -> PhotoAssetType.ORIGINAL;
            case WORKING -> PhotoAssetType.WORKING;
            case THUMBNAIL -> PhotoAssetType.THUMBNAIL;
        };
    }

    private PhotoUploadKind uploadKind(PhotoAssetType assetType) {
        return switch (assetType) {
            case ORIGINAL -> PhotoUploadKind.ORIGINAL;
            case WORKING -> PhotoUploadKind.WORKING;
            case THUMBNAIL -> PhotoUploadKind.THUMBNAIL;
        };
    }

    private String normalizeHash(String hash) {
        return hash.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeMime(String mimeType) {
        var normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        return "image/jpg".equals(normalized) ? "image/jpeg" : normalized;
    }

    private String previewFileName(Long photoId, PhotoAssetType assetType, String mimeType) {
        return "photo-%d-%s.%s".formatted(
                photoId,
                assetType.name().toLowerCase(Locale.ROOT),
                extension(mimeType));
    }

    private String extension(String mimeType) {
        return switch (mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "bin";
        };
    }

    private String normalizeStepCode(String stepCode) {
        if (stepCode == null || stepCode.isBlank()) {
            return null;
        }
        return stepCode.trim().toUpperCase(Locale.ROOT);
    }

    private record ParentRef(Long projectId, Long reportId, InspectionReport report) {
    }

    private void publishAfterCommit(Object event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventBus.publish(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventBus.publish(event);
            }
        });
    }
}
