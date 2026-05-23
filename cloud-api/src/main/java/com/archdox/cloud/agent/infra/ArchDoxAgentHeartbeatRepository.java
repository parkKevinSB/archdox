package com.archdox.cloud.agent.infra;

import com.archdox.cloud.agent.domain.ArchDoxAgentHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchDoxAgentHeartbeatRepository extends JpaRepository<ArchDoxAgentHeartbeat, Long> {
}
