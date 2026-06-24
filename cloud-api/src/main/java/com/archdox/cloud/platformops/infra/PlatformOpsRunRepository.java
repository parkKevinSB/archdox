package com.archdox.cloud.platformops.infra;

import com.archdox.cloud.platformops.domain.PlatformOpsRun;
import com.archdox.cloud.platformops.domain.PlatformOpsRunStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsRunTriggerType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformOpsRunRepository extends JpaRepository<PlatformOpsRun, Long> {
    Optional<PlatformOpsRun> findByAiHarnessRunId(String aiHarnessRunId);

    List<PlatformOpsRun> findByStatus(PlatformOpsRunStatus status);

    boolean existsByTriggerTypeAndStatus(PlatformOpsRunTriggerType triggerType, PlatformOpsRunStatus status);

    boolean existsByTriggerTypeInAndStatus(List<PlatformOpsRunTriggerType> triggerTypes, PlatformOpsRunStatus status);

    boolean existsByIncidentIdAndTriggerTypeInAndStatus(
            Long incidentId,
            List<PlatformOpsRunTriggerType> triggerTypes,
            PlatformOpsRunStatus status);

    boolean existsByIncidentIdAndTriggerTypeInAndStartedAtGreaterThanEqual(
            Long incidentId,
            List<PlatformOpsRunTriggerType> triggerTypes,
            OffsetDateTime startedAt);

    Optional<PlatformOpsRun> findFirstByTriggerTypeOrderByStartedAtDescIdDesc(PlatformOpsRunTriggerType triggerType);

    long countByStatusAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            PlatformOpsRunStatus status,
            OffsetDateTime from,
            OffsetDateTime to);

    long countByStatusAndFailureCodeAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            PlatformOpsRunStatus status,
            String failureCode,
            OffsetDateTime from,
            OffsetDateTime to);

    @Query("""
            select count(run)
            from PlatformOpsRun run
            where run.status = :status
              and (run.failureCode is null or run.failureCode <> :excludedFailureCode)
              and run.startedAt >= :from
              and run.startedAt < :to
            """)
    long countByStatusAndFailureCodeOtherThan(
            @Param("status") PlatformOpsRunStatus status,
            @Param("excludedFailureCode") String excludedFailureCode,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from PlatformOpsRun run
            where run.status in :terminalStatuses
              and run.startedAt < :cutoff
              and not exists (
                  select 1
                  from PlatformOpsFinding finding
                  where finding.runId = run.id
              )
              and not exists (
                  select 1
                  from PlatformOpsDailyReport dailyReport
                  where dailyReport.runId = run.id
              )
            """)
    int deleteUnreferencedTerminalRunsBefore(
            @Param("cutoff") OffsetDateTime cutoff,
            @Param("terminalStatuses") List<PlatformOpsRunStatus> terminalStatuses);

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
