package com.archdox.cloud.photo.dto;

import com.archdox.cloud.photo.domain.PhotoCaptureKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreatePhotoUploadIntentRequest(
        Long projectId,
        Long reportId,
        @Size(max = 100) String stepCode,
        Long checklistItemId,
        PhotoCaptureKind captureKind,
        @NotBlank @Size(max = 100) String mime,
        @NotNull @Positive Long bytes,
        @NotBlank @Size(max = 120) String hash,
        Integer width,
        Integer height,
        OffsetDateTime takenAt,
        BigDecimal gpsLat,
        BigDecimal gpsLng,
        Boolean wantsOriginal
) {
}
