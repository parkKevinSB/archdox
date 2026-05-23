package com.archdox.cloud.agent.infra;

import com.archdox.cloud.agent.domain.ArchDoxAgentCommand;
import com.archdox.cloud.agent.domain.ArchDoxAgentCommandStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ArchDoxAgentCommandRepository extends JpaRepository<ArchDoxAgentCommand, Long> {
    @EntityGraph(attributePaths = "agent")
    List<ArchDoxAgentCommand> findByAgentIdAndStatusInOrderByCreatedAtAsc(
            Long agentId,
            Collection<ArchDoxAgentCommandStatus> statuses);

    @EntityGraph(attributePaths = "agent")
    List<ArchDoxAgentCommand> findByStatusInOrderByCreatedAtAsc(
            Collection<ArchDoxAgentCommandStatus> statuses);

    @EntityGraph(attributePaths = "agent")
    Optional<ArchDoxAgentCommand> findById(Long id);

    long countByAgentIdAndStatusIn(Long agentId, Collection<ArchDoxAgentCommandStatus> statuses);

    @Query("""
            select count(command)
            from ArchDoxAgentCommand command
            join command.agent agent
            where agent.officeId = :officeId
              and command.status in :statuses
            """)
    long countByOfficeIdAndStatusIn(Long officeId, Collection<ArchDoxAgentCommandStatus> statuses);

    @EntityGraph(attributePaths = "agent")
    @Query("""
            select command
            from ArchDoxAgentCommand command
            join command.agent agent
            where agent.officeId = :officeId
              and (:agentId is null or agent.id = :agentId)
              and (:status is null or command.status = :status)
            order by command.createdAt desc, command.id desc
            """)
    List<ArchDoxAgentCommand> searchOfficeCommands(
            Long officeId,
            Long agentId,
            ArchDoxAgentCommandStatus status,
            Pageable pageable);
}
