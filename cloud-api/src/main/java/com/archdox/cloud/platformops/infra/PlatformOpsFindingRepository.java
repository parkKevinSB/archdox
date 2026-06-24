package com.archdox.cloud.platformops.infra;

import com.archdox.cloud.platformops.domain.PlatformOpsFinding;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSource;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformOpsFindingRepository extends JpaRepository<PlatformOpsFinding, Long> {
    List<PlatformOpsFinding> findByIncidentIdOrderByCreatedAtDescIdDesc(Long incidentId, Pageable pageable);

    List<PlatformOpsFinding> findByRunIdOrderByCreatedAtAscIdAsc(Long runId);

    List<PlatformOpsFinding> findBySourceAndWorkflowTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            PlatformOpsFindingSource source,
            String workflowType,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable);

    void deleteByRunIdAndSource(Long runId, PlatformOpsFindingSource source);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from PlatformOpsFinding finding
            where finding.createdAt < :cutoff
            """)
    int deleteCreatedBefore(@Param("cutoff") OffsetDateTime cutoff);

    long countByRunIdAndSource(Long runId, PlatformOpsFindingSource source);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(OffsetDateTime from, OffsetDateTime to);

    @Query("""
            select finding.severity as severity,
                   count(finding.id) as findingCount
            from PlatformOpsFinding finding
            where finding.createdAt >= :from
              and finding.createdAt < :to
            group by finding.severity
            order by count(finding.id) desc
            """)
    List<FindingSeverityCountProjection> summarizeSeverity(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Query("""
            select finding
            from PlatformOpsFinding finding
            where (:officeId is null or finding.officeId = :officeId)
              and (:runId is null or finding.runId = :runId)
              and (:incidentId is null or finding.incidentId = :incidentId)
              and (:severity is null or finding.severity = :severity)
              and (:source is null or finding.source = :source)
              and (:category is null or finding.category = :category)
            order by finding.createdAt desc, finding.id desc
            """)
    List<PlatformOpsFinding> search(
            @Param("officeId") Long officeId,
            @Param("runId") Long runId,
            @Param("incidentId") Long incidentId,
            @Param("severity") PlatformOpsFindingSeverity severity,
            @Param("source") PlatformOpsFindingSource source,
            @Param("category") String category,
            Pageable pageable);

    interface FindingSeverityCountProjection {
        PlatformOpsFindingSeverity getSeverity();

        Long getFindingCount();
    }
}
