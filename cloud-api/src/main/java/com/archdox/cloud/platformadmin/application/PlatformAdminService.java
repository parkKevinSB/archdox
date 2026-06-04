package com.archdox.cloud.platformadmin.application;

import com.archdox.cloud.global.api.ForbiddenException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.domain.PlatformAdmin;
import com.archdox.cloud.platformadmin.domain.PlatformAdminRole;
import com.archdox.cloud.platformadmin.domain.PlatformAdminStatus;
import com.archdox.cloud.platformadmin.dto.PlatformAdminMeResponse;
import com.archdox.cloud.platformadmin.infra.PlatformAdminRepository;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAdminService {
    private final PlatformAdminRepository repository;

    public PlatformAdminService(PlatformAdminRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PlatformAdminMeResponse me(UserPrincipal principal) {
        var admin = requirePlatformAdmin(principal);
        return new PlatformAdminMeResponse(principal.userId(), principal.email(), admin.role());
    }

    @Transactional(readOnly = true)
    public boolean isPlatformAdmin(UserPrincipal principal) {
        return principal != null && isPlatformAdmin(principal.userId());
    }

    @Transactional(readOnly = true)
    public boolean isPlatformAdmin(Long userId) {
        return userId != null && repository.findByUserIdAndStatus(userId, PlatformAdminStatus.ACTIVE).isPresent();
    }

    @Transactional(readOnly = true)
    public PlatformAdmin requirePlatformAdmin(UserPrincipal principal, PlatformAdminRole... allowedRoles) {
        if (principal == null) {
            throw forbidden();
        }
        var admin = repository.findByUserIdAndStatus(principal.userId(), PlatformAdminStatus.ACTIVE)
                .orElseThrow(this::forbidden);
        if (allowedRoles != null && allowedRoles.length > 0) {
            Set<PlatformAdminRole> allowed = Arrays.stream(allowedRoles).collect(Collectors.toSet());
            if (!allowed.contains(admin.role())) {
                throw forbidden();
            }
        }
        return admin;
    }

    private ForbiddenException forbidden() {
        return new ForbiddenException(
                "PLATFORM_ADMIN_REQUIRED",
                "error.platformAdmin.required",
                "Platform admin permission is required");
    }
}
