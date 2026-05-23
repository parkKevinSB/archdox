package com.archdox.cloud.photo.dto;

import com.archdox.cloud.photo.domain.PhotoUploadTarget;
import java.time.OffsetDateTime;
import java.util.List;

public record PhotoUploadIntentResponse(
        Long photoId,
        PhotoUploadTarget target,
        boolean uploadRequired,
        List<PhotoUploadInstructionResponse> uploads,
        Long mediationJobId,
        OffsetDateTime expiresAt,
        PhotoResponse photo
) {
}
