package com.archdox.cloud.platformops.infra;

import com.archdox.cloud.platformops.domain.PlatformOpsRun;
import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRunTriggerType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformOpsRunRepository extends JpaRepository<PlatformOpsRun, Long> {
    Optional<PlatformOpsRun> findByAiHarnessRunId(String aiHarnessRunId);

    boolean existsByTriggerTypeAndStatus(PlatformOpsRunTriggerType triggerType, PlatformOpsRunStatus status);

    Optional<PlatformOpsRun> findFirstByTriggerTypeOrderByStartedAtDescIdDesc(PlatformOpsRunTriggerType triggerType);

    long countByStatusAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            PlatformOpsRunStatus status,
            OffsetDateTime from,
            OffsetDateTime to);

    @Query("""
            select run
            from PlatformOpsRun run
            where (:status is null or run.status = :status)
              and (:triggerType is null or run.triggerType = :triggerType)
            order by run.startedAt desc, run.id desc
            """)
    List<PlatformOpsRun> search(
            @Param("status") PlatformOpsRunStatus status,
            @Param("triggerType") PlatformOpsRunTriggerType triggerType,
            Pageable pageable);
}
