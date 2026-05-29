package com.archdox.cloud.auth.infra;

import com.archdox.cloud.auth.domain.AuthLoginGuard;
import com.archdox.cloud.auth.domain.AuthLoginGuardScope;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthLoginGuardRepository extends JpaRepository<AuthLoginGuard, Long> {
    Optional<AuthLoginGuard> findByScopeAndGuardKeyHash(AuthLoginGuardScope scope, String guardKeyHash);
}
