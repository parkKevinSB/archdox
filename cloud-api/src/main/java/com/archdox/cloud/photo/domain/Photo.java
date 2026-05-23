package com.archdox.cloud.photo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "photos")
public class Photo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "step_code")
    private String stepCode;

    @Column(name = "checklist_item_id")
    private Long checklistItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_kind", nullable = false)
    private PhotoCaptureKind captureKind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PhotoStatus status;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    private Integer width;

    private Integer height;

    private Long bytes;

    @Column(name = "hash_sha256", nullable = false)
    private String hashSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_kind", nullable = false)
    private PhotoStorageKind storageKind;

    @Column(name = "storage_ref", nullable = false)
    private String storageRef;

    @Column(name = "thumbnail_storage_ref")
    private String thumbnailStorageRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_target", nullable = false)
    private PhotoUploadTarget uploadTarget;

    @Enumerated(EnumType.STRING)
    @Column(name = "original_pickup_status", nullable = false)
    private PhotoPickupStatus originalPickupStatus;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "confirmed_by")
    private Long confirmedBy;

    @Column(name = "taken_at")
    private OffsetDateTime takenAt;

    @Column(name = "gps_lat", precision = 9, scale = 6)
    private BigDecimal gpsLat;

    @Column(name = "gps_lng", precision = 9, scale = 6)
    private BigDecimal gpsLng;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "original_picked_up_at")
    private OffsetDateTime originalPickedUpAt;

    @Column(name = "original_temporary_deleted_at")
    private OffsetDateTime originalTemporaryDeletedAt;

    @Column(name = "pickup_error_message")
    private String pickupErrorMessage;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Photo() {
    }

    public Photo(
            Long officeId,
            Long projectId,
            Long reportId,
            String stepCode,
            Long checklistItemId,
            PhotoCaptureKind captureKind,
            String mimeType,
            Long bytes,
            String hashSha256,
            PhotoStorageKind storageKind,
            String storageRef,
            String thumbnailStorageRef,
            PhotoUploadTarget uploadTarget,
            Long requestedBy,
            OffsetDateTime takenAt,
            BigDecimal gpsLat,
            BigDecimal gpsLng,
            OffsetDateTime now
    ) {
        this.officeId = officeId;
        this.projectId = projectId;
        this.reportId = reportId;
        this.stepCode = stepCode;
        this.checklistItemId = checklistItemId;
        this.captureKind = captureKind;
        this.status = PhotoStatus.PENDING_UPLOAD;
        this.mimeType = mimeType;
        this.bytes = bytes;
        this.hashSha256 = hashSha256;
        this.storageKind = storageKind;
        this.storageRef = storageRef;
        this.thumbnailStorageRef = thumbnailStorageRef;
        this.uploadTarget = uploadTarget;
        this.originalPickupStatus = PhotoPickupStatus.PENDING;
        this.requestedBy = requestedBy;
        this.takenAt = takenAt;
        this.gpsLat = gpsLat;
        this.gpsLng = gpsLng;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void confirm(Long bytes, Integer width, Integer height, Long confirmedBy, OffsetDateTime now) {
        this.bytes = bytes;
        this.width = width;
        this.height = height;
        this.confirmedBy = confirmedBy;
        this.confirmedAt = now;
        this.updatedAt = now;
        this.status = PhotoStatus.UPLOADED;
    }

    public void markOriginalPickupNotRequired(OffsetDateTime now) {
        this.originalPickupStatus = PhotoPickupStatus.NOT_REQUIRED;
        this.updatedAt = now;
    }

    public void markOriginalPickedUp(OffsetDateTime now) {
        this.originalPickupStatus = PhotoPickupStatus.PICKED_UP;
        this.originalPickedUpAt = now;
        this.pickupErrorMessage = null;
        this.updatedAt = now;
    }

    public void markOriginalPickupFailed(String errorMessage, OffsetDateTime now) {
        this.originalPickupStatus = PhotoPickupStatus.FAILED;
        this.pickupErrorMessage = errorMessage;
        this.updatedAt = now;
    }

    public void markOriginalTemporaryDeleted(OffsetDateTime now) {
        this.originalTemporaryDeletedAt = now;
        this.updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public Long officeId() {
        return officeId;
    }

    public Long projectId() {
        return projectId;
    }

    public Long reportId() {
        return reportId;
    }

    public String stepCode() {
        return stepCode;
    }

    public Long checklistItemId() {
        return checklistItemId;
    }

    public PhotoCaptureKind captureKind() {
        return captureKind;
    }

    public PhotoStatus status() {
        return status;
    }

    public String mimeType() {
        return mimeType;
    }

    public Integer width() {
        return width;
    }

    public Integer height() {
        return height;
    }

    public Long bytes() {
        return bytes;
    }

    public String hashSha256() {
        return hashSha256;
    }

    public PhotoStorageKind storageKind() {
        return storageKind;
    }

    public String storageRef() {
        return storageRef;
    }

    public String thumbnailStorageRef() {
        return thumbnailStorageRef;
    }

    public PhotoUploadTarget uploadTarget() {
        return uploadTarget;
    }

    public PhotoPickupStatus originalPickupStatus() {
        return originalPickupStatus;
    }

    public OffsetDateTime originalPickedUpAt() {
        return originalPickedUpAt;
    }

    public OffsetDateTime originalTemporaryDeletedAt() {
        return originalTemporaryDeletedAt;
    }

    public Long requestedBy() {
        return requestedBy;
    }

    public Long confirmedBy() {
        return confirmedBy;
    }

    public OffsetDateTime takenAt() {
        return takenAt;
    }

    public boolean hasGps() {
        return gpsLat != null && gpsLng != null;
    }

    public OffsetDateTime createdAt() {
        return createdAt;
    }

    public OffsetDateTime confirmedAt() {
        return confirmedAt;
    }

    public String pickupErrorMessage() {
        return pickupErrorMessage;
    }

    public OffsetDateTime updatedAt() {
        return updatedAt;
    }
}
