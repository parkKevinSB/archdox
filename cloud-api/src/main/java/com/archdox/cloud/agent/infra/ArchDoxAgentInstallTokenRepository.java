package com.archdox.cloud.agent.infra;

import com.archdox.cloud.agent.domain.ArchDoxAgentInstallToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchDoxAgentInstallTokenRepository extends JpaRepository<ArchDoxAgentInstallToken, Long> {
    Optional<ArchDoxAgentInstallToken> findByTokenHash(String tokenHash);
}
