package com.archdox.cloud.platformadmin.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.archdox.cloud.global.api.ForbiddenException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.domain.PlatformAdmin;
import com.archdox.cloud.platformadmin.domain.PlatformAdminRole;
import com.archdox.cloud.platformadmin.domain.PlatformAdminStatus;
import com.archdox.cloud.platformadmin.infra.PlatformAdminRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlatformAdminServiceTest {
    private final PlatformAdminRepository repository = mock(PlatformAdminRepository.class);
    private final PlatformAdminService service = new PlatformAdminService(repository);

    @Test
    void activePlatformAdminCanReadMe() {
        var principal = new UserPrincipal(1L, "admin@example.com");
        when(repository.findByUserIdAndStatus(1L, PlatformAdminStatus.ACTIVE))
                .thenReturn(Optional.of(new PlatformAdmin(1L, PlatformAdminRole.SUPER_ADMIN, OffsetDateTime.now())));

        var response = service.me(principal);

        assertEquals(1L, response.userId());
        assertEquals("admin@example.com", response.email());
        assertEquals(PlatformAdminRole.SUPER_ADMIN, response.role());
    }

    @Test
    void officeUserWithoutPlatformRoleIsForbidden() {
        var principal = new UserPrincipal(1L, "office-admin@example.com");
        when(repository.findByUserIdAndStatus(1L, PlatformAdminStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> service.me(principal));
    }
}
