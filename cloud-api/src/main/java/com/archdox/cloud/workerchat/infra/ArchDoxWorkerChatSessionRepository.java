package com.archdox.cloud.workerchat.infra;

import com.archdox.cloud.workerchat.domain.ArchDoxWorkerChatSession;
import com.archdox.cloud.workerchat.domain.ArchDoxWorkerChatSessionStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchDoxWorkerChatSessionRepository extends JpaRepository<ArchDoxWorkerChatSession, Long> {
    Optional<ArchDoxWorkerChatSession> findFirstByOfficeIdAndProjectIdAndUserIdAndStatusOrderByUpdatedAtDesc(
            Long officeId,
            Long projectId,
            Long userId,
            ArchDoxWorkerChatSessionStatus status);

    Optional<ArchDoxWorkerChatSession> findByIdAndOfficeIdAndProjectIdAndUserId(
            Long id,
            Long officeId,
            Long projectId,
            Long userId);

    Optional<ArchDoxWorkerChatSession> findByIdAndOfficeId(Long id, Long officeId);
}
