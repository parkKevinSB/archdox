package com.archdox.cloud.agent.infra;

import com.archdox.cloud.agent.domain.ArchDoxAgent;
import com.archdox.cloud.agent.domain.ArchDoxAgentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchDoxAgentRepository extends JpaRepository<ArchDoxAgent, Long> {
    Optional<ArchDoxAgent> findByOfficeIdAndAgentCode(Long officeId, String agentCode);

    Optional<ArchDoxAgent> findFirstByOfficeIdOrderByLastSeenAtDesc(Long officeId);

    List<ArchDoxAgent> findByOfficeIdAndStatusOrderByLastSeenAtDesc(Long officeId, ArchDoxAgentStatus status);

    List<ArchDoxAgent> findByOfficeIdOrderByLastSeenAtDesc(Long officeId, Pageable pageable);

    List<ArchDoxAgent> findAllByOrderByLastSeenAtDesc(Pageable pageable);

    long countByOfficeId(Long officeId);

    long countByOfficeIdAndStatus(Long officeId, ArchDoxAgentStatus status);

    long countByStatus(ArchDoxAgentStatus status);
}
