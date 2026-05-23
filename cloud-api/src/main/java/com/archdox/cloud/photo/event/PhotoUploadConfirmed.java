package com.archdox.cloud.photo.event;

import java.time.OffsetDateTime;

public record PhotoUploadConfirmed(
        Long officeId,
        Long photoId,
        Long reportId,
        Long projectId,
        OffsetDateTime occurredAt
) {
}
