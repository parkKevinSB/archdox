package com.archdox.cloud.engine.infra;

import com.archdox.cloud.engine.domain.EngineReviewSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EngineReviewSessionRepository extends JpaRepository<EngineReviewSession, Long> {
    Optional<EngineReviewSession> findByExternalSessionIdAndOwnerUserId(String externalSessionId, Long ownerUserId);

    Optional<EngineReviewSession> findByExternalSessionIdAndOwnerUserIdAndOfficeId(
            String externalSessionId,
            Long ownerUserId,
            Long officeId);

    Optional<EngineReviewSession> findByExternalSessionIdAndOwnerUserIdAndOfficeIdIsNull(
            String externalSessionId,
            Long ownerUserId);
}
