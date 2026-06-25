package com.archdox.cloud.photo.application;

import com.archdox.cloud.checklist.application.ChecklistService;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.ConflictException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.application.InspectionReportService;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.application.OfficePermissionService;
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
import com.archdox.cloud.site.application.SiteService;
import com.archdox.cloud.supervisionledger.domain.SiteSupervisionEntry;
import com.archdox.cloud.supervisionledger.infra.SiteSupervisionEntryRepository;
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
    private final PhotoRepository photoRepository;
    private final PhotoAssetRepository photoAssetRepository;
    private final ProjectService projectService;
    private final SiteService siteService;
    private final InspectionReportService inspectionReportService;
    private final OfficePermissionService permissionService;
    private final ChecklistService checklistService;
    private final SiteSupervisionEntryRepository supervisionEntryRepository;
    private final PhotoStorageRefFactory storageRefFactory;
    private final PhotoStorageAdapterResolver storageAdapterResolver;
    private final PhotoStorageProperties storageProperties;
    private final EventBus eventBus;

    public PhotoService(
            PhotoRepository photoRepository,
            PhotoAssetRepository photoAssetRepository,
            ProjectService projectService,
            SiteService siteService,
            InspectionReportService inspectionReportService,
            OfficePermissionService permissionService,
            ChecklistService checklistService,
            SiteSupervisionEntryRepository supervisionEntryRepository,
            PhotoStorageRefFactory storageRefFactory,
            PhotoStorageAdapterResolver storageAdapterResolver,
            PhotoStorageProperties storageProperties,
            EventBus eventBus
    ) {
        this.photoRepository = photoRepository;
        this.photoAssetRepository = photoAssetRepository;
        this.projectService = projectService;
        this.siteService = siteService;
        this.inspectionReportService = inspectionReportService;
        this.permissionService = permissionService;
        this.checklistService = checklistService;
        this.supervisionEntryRepository = supervisionEntryRepository;
        this.storageRefFactory = storageRefFactory;
        this.storageAdapterResolver = storageAdapterResolver;
        this.storageProperties = storageProperties;
        this.eventBus = eventBus;
    }

    @Transactional
    public PhotoUploadIntentResponse createIntent(CreatePhotoUploadIntentRequest request, UserPrincipal principal) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var hash = normalizeHash(request.hash());
        var resolved = resolveParent(request);
        var evidenceContext = resolveEvidenceContext(request, resolved, officeId);
        validateChecklistLink(request, resolved);
        var existing = photoRepository.findFirstByOfficeIdAndHashSha256AndStatus(
                officeId,
                hash,
                PhotoStatus.UPLOADED);
        if (existing.isPresent() && isSameUploadContext(existing.get(), resolved, evidenceContext, request)) {
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
                evidenceContext.siteId(),
                resolved.reportId(),
                normalizeStepCode(request.stepCode()),
                request.checklistItemId(),
                evidenceContext.siteSupervisionEntryId(),
                evidenceContext.tradeCode(),
                evidenceContext.processCode(),
                evidenceContext.inspectionItemCode(),
                evidenceContext.caption(),
                evidenceContext.locationNote(),
                evidenceContext.drawingRef(),
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
        if (!includeOriginal || uploadTarget != PhotoUploadTarget.CLOUD_MEDIATED) {
            saved.markOriginalPickupNotRequired(now);
        }
        var assets = createAssets(saved, refs, mimeType, request.bytes(), hash, includeOriginal, uploadTarget, adapter, now);
        photoAssetRepository.saveAll(assets);
        var expiresAt = now.plusMinutes(Math.max(1, storageProperties.getUploadTtlMinutes()));
        return new PhotoUploadIntentResponse(
                saved.id(),
                saved.uploadTarget(),
                true,
                adapter.createUploadInstructions(saved, assets, expiresAt),
                null,
                expiresAt,
                toResponse(saved));
    }

    @Transactional
    public List<PhotoResponse> listByReport(Long reportId) {
        var report = inspectionReportService.requireReport(reportId);
        var officeId = OfficeContext.requireCurrentOfficeId();
        expireStalePendingUploads(officeId, report.id(), OffsetDateTime.now());
        return photoRepository.findByOfficeIdAndReportIdAndStatusNotOrderByIdDesc(officeId, report.id(), PhotoStatus.DELETED).stream()
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
        try (input) {
            var content = input.readAllBytes();
            return new PhotoAssetContent(
                    previewFileName(photo.id(), assetType, asset.mimeType()),
                    asset.mimeType(),
                    asset.bytes(),
                    content);
        }
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
    public void cancelPendingUpload(Long photoId) {
        var photo = requirePhoto(photoId);
        if (photo.status() != PhotoStatus.PENDING_UPLOAD) {
            throw new BadRequestException("Only pending photo uploads can be cancelled");
        }
        markUploadAbandoned(photo, OffsetDateTime.now());
    }

    @Transactional
    public void delete(Long photoId, UserPrincipal principal) {
        var photo = requirePhoto(photoId);
        if (photo.status() == PhotoStatus.DELETED) {
            return;
        }
        if (photo.reportId() != null) {
            var report = inspectionReportService.requireReport(photo.reportId());
            permissionService.requireReportWriter(principal.userId(), report.officeId(), report.projectId(), report.id());
        } else if (photo.projectId() != null) {
            permissionService.requireReportWriter(principal.userId(), photo.officeId(), photo.projectId(), null);
        } else {
            permissionService.requireReportWriter(principal.userId(), photo.officeId());
        }
        var now = OffsetDateTime.now();
        markUploadAbandoned(photo, now);
        if (photo.reportId() != null) {
            inspectionReportService.removePhotoReference(photo.reportId(), photo.id(), principal.userId(), now);
        }
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
        asset.markUploaded(contentLength == null || contentLength <= 0 ? null : contentLength, OffsetDateTime.now());
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
            if (request.siteId() != null && report.siteId() != null && !request.siteId().equals(report.siteId())) {
                throw new BadRequestException("siteId does not match the report's site");
            }
            if (request.siteId() != null) {
                siteService.requireSiteForProject(request.siteId(), report.projectId());
            }
            return new ParentRef(report.projectId(), report.siteId() == null ? request.siteId() : report.siteId(), report.id(), report);
        }
        if (request.projectId() == null) {
            throw new BadRequestException("projectId or reportId is required");
        }
        var project = projectService.requireProject(request.projectId());
        if (request.siteId() != null) {
            siteService.requireSiteForProject(request.siteId(), project.id());
        }
        return new ParentRef(project.id(), request.siteId(), null, null);
    }

    private PhotoEvidenceContext resolveEvidenceContext(
            CreatePhotoUploadIntentRequest request,
            ParentRef parent,
            Long officeId
    ) {
        SiteSupervisionEntry entry = null;
        if (request.siteSupervisionEntryId() != null) {
            entry = supervisionEntryRepository.findByIdAndOfficeId(request.siteSupervisionEntryId(), officeId)
                    .orElseThrow(() -> new BadRequestException("siteSupervisionEntryId does not match the current office"));
            if (!entry.projectId().equals(parent.projectId())) {
                throw new BadRequestException("siteSupervisionEntryId does not match the photo project");
            }
            if (parent.siteId() != null && !entry.siteId().equals(parent.siteId())) {
                throw new BadRequestException("siteSupervisionEntryId does not match the photo site");
            }
        }
        var siteId = parent.siteId();
        if (siteId == null && entry != null) {
            siteId = entry.siteId();
        }
        return new PhotoEvidenceContext(
                siteId,
                request.siteSupervisionEntryId(),
                firstNonBlank(normalizeCode(request.tradeCode()), entry == null ? null : entry.tradeCode()),
                firstNonBlank(normalizeCode(request.processCode()), entry == null ? null : entry.processCode()),
                firstNonBlank(normalizeCode(request.inspectionItemCode()), entry == null ? null : entry.inspectionItemCode()),
                trimToNull(request.caption()),
                trimToNull(request.locationNote()),
                trimToNull(request.drawingRef()));
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

    private boolean isSameUploadContext(
            Photo photo,
            ParentRef resolved,
            PhotoEvidenceContext evidenceContext,
            CreatePhotoUploadIntentRequest request
    ) {
        return Objects.equals(photo.projectId(), resolved.projectId())
                && Objects.equals(photo.siteId(), evidenceContext.siteId())
                && Objects.equals(photo.reportId(), resolved.reportId())
                && Objects.equals(photo.checklistItemId(), request.checklistItemId())
                && Objects.equals(photo.siteSupervisionEntryId(), evidenceContext.siteSupervisionEntryId())
                && Objects.equals(photo.tradeCode(), evidenceContext.tradeCode())
                && Objects.equals(photo.processCode(), evidenceContext.processCode())
                && Objects.equals(photo.inspectionItemCode(), evidenceContext.inspectionItemCode())
                && Objects.equals(photo.caption(), evidenceContext.caption())
                && Objects.equals(photo.locationNote(), evidenceContext.locationNote())
                && Objects.equals(photo.drawingRef(), evidenceContext.drawingRef())
                && Objects.equals(photo.stepCode(), normalizeStepCode(request.stepCode()));
    }

    private Photo requirePhoto(Long photoId) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        return photoRepository.findByIdAndOfficeId(photoId, officeId)
                .orElseThrow(() -> new NotFoundException("Photo not found"));
    }

    private void expireStalePendingUploads(Long officeId, Long reportId, OffsetDateTime now) {
        var graceMinutes = storageProperties.getPendingUploadCleanupGraceMinutes();
        if (graceMinutes < 0) {
            return;
        }
        var cutoff = now.minusMinutes(graceMinutes);
        photoRepository.findByOfficeIdAndReportIdAndStatusAndCreatedAtBefore(
                        officeId,
                        reportId,
                        PhotoStatus.PENDING_UPLOAD,
                        cutoff)
                .forEach(photo -> markUploadAbandoned(photo, now));
    }

    private void markUploadAbandoned(Photo photo, OffsetDateTime now) {
        var assets = photoAssetRepository.findByPhotoIdOrderById(photo.id());
        for (var asset : assets) {
            try {
                storageAdapterResolver.forStorageKind(asset.storageKind()).deleteIfExists(asset.storageRef());
            } catch (IOException | RuntimeException ignored) {
                // Stale upload cleanup should not block the report screen.
            }
            asset.markDeleted(now);
        }
        photo.markDeleted(now);
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
                photo.siteId(),
                photo.reportId(),
                photo.stepCode(),
                photo.checklistItemId(),
                photo.siteSupervisionEntryId(),
                photo.tradeCode(),
                photo.processCode(),
                photo.inspectionItemCode(),
                photo.caption(),
                photo.locationNote(),
                photo.drawingRef(),
                contextLabel(photo),
                contextDescription(photo),
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

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? trimToNull(fallback) : first;
    }

    private String contextLabel(Photo photo) {
        if (photo.caption() != null && !photo.caption().isBlank()) {
            return photo.caption();
        }
        if (photo.inspectionItemCode() != null && !photo.inspectionItemCode().isBlank()) {
            return photo.inspectionItemCode();
        }
        if (photo.stepCode() != null && !photo.stepCode().isBlank()) {
            return photo.stepCode();
        }
        return "Photo " + photo.id();
    }

    private String contextDescription(Photo photo) {
        var parts = new java.util.ArrayList<String>();
        if (photo.siteId() != null) {
            parts.add("site:" + photo.siteId());
        }
        if (photo.tradeCode() != null) {
            parts.add("trade:" + photo.tradeCode());
        }
        if (photo.processCode() != null) {
            parts.add("process:" + photo.processCode());
        }
        if (photo.inspectionItemCode() != null) {
            parts.add("inspectionItem:" + photo.inspectionItemCode());
        }
        if (photo.locationNote() != null) {
            parts.add(photo.locationNote());
        }
        return parts.isEmpty() ? null : String.join(" / ", parts);
    }

    private record ParentRef(Long projectId, Long siteId, Long reportId, InspectionReport report) {
    }

    private record PhotoEvidenceContext(
            Long siteId,
            Long siteSupervisionEntryId,
            String tradeCode,
            String processCode,
            String inspectionItemCode,
            String caption,
            String locationNote,
            String drawingRef
    ) {
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
