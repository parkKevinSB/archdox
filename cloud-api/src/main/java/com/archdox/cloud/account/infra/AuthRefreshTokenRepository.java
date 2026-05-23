package com.archdox.cloud.account.infra;

import com.archdox.cloud.account.domain.AuthRefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, Long> {
    Optional<AuthRefreshToken> findByTokenHash(String tokenHash);
}
