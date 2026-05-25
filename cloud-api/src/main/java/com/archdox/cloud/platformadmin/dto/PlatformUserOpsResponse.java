package com.archdox.cloud.platformadmin.dto;

import com.archdox.cloud.account.domain.UserStatus;
import java.time.OffsetDateTime;

public record PlatformUserOpsResponse(
        Long id,
        String email,
        String name,
        UserStatus status,
        OffsetDateTime createdAt
) {
}
