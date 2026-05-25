package com.archdox.cloud.agent.infra;

import com.archdox.cloud.agent.domain.ArchDoxAgentSession;
import com.archdox.cloud.agent.domain.ArchDoxAgentSessionStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ArchDoxAgentSessionRepository extends JpaRepository<ArchDoxAgentSession, Long> {
    Optional<ArchDoxAgentSession> findByApiInstanceIdAndWebsocketSessionId(
            String apiInstanceId,
            String websocketSessionId);

    List<ArchDoxAgentSession> findByAgentIdAndStatus(Long agentId, ArchDoxAgentSessionStatus status);

    List<ArchDoxAgentSession> findByAgentIdOrderByConnectedAtAsc(Long agentId);

    List<ArchDoxAgentSession> findByOfficeIdAndStatusOrderByLastSeenAtDesc(
            Long officeId,
            ArchDoxAgentSessionStatus status);

    List<ArchDoxAgentSession> findByOfficeIdOrderByLastSeenAtDesc(Long officeId, Pageable pageable);

    boolean existsByAgentIdAndStatus(Long agentId, ArchDoxAgentSessionStatus status);

    long countByOfficeIdAndStatus(Long officeId, ArchDoxAgentSessionStatus status);

    long countByStatus(ArchDoxAgentSessionStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ArchDoxAgentSession session
            set session.status = :status,
                session.lastSeenAt = :disconnectedAt,
                session.disconnectedAt = :disconnectedAt,
                session.disconnectReason = :disconnectReason
            where session.apiInstanceId = :apiInstanceId
              and session.status = :activeStatus
            """)
    int markActiveSessionsDisconnectedForApiInstance(
            String apiInstanceId,
            ArchDoxAgentSessionStatus activeStatus,
            ArchDoxAgentSessionStatus status,
            OffsetDateTime disconnectedAt,
            String disconnectReason);
}
