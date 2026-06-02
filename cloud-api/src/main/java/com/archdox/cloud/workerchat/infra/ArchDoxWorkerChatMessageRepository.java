package com.archdox.cloud.workerchat.infra;

import com.archdox.cloud.workerchat.domain.ArchDoxWorkerChatMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArchDoxWorkerChatMessageRepository extends JpaRepository<ArchDoxWorkerChatMessage, Long> {
    List<ArchDoxWorkerChatMessage> findByOfficeIdAndSessionIdOrderByCreatedAtAscIdAsc(Long officeId, Long sessionId);

    boolean existsByOfficeIdAndSessionId(Long officeId, Long sessionId);

    Optional<ArchDoxWorkerChatMessage> findByIdAndOfficeIdAndSessionId(Long id, Long officeId, Long sessionId);

    void deleteByOfficeIdAndSessionId(Long officeId, Long sessionId);
}
