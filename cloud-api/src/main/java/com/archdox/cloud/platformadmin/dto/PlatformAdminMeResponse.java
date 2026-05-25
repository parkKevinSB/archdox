package com.archdox.cloud.platformadmin.dto;

import com.archdox.cloud.platformadmin.domain.PlatformAdminRole;

public record PlatformAdminMeResponse(
        Long userId,
        String email,
        PlatformAdminRole role
) {
}
