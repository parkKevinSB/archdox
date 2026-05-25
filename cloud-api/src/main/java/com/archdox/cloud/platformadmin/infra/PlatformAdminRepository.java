package com.archdox.cloud.platformadmin.infra;

import com.archdox.cloud.platformadmin.domain.PlatformAdmin;
import com.archdox.cloud.platformadmin.domain.PlatformAdminStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, Long> {
    Optional<PlatformAdmin> findByUserIdAndStatus(Long userId, PlatformAdminStatus status);

    boolean existsByUserId(Long userId);
}
