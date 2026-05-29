package com.archdox.cloud.platformops.infra;

import com.archdox.cloud.platformops.domain.PlatformOpsFinding;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSource;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformOpsFindingRepository extends JpaRepository<PlatformOpsFinding, Long> {
    List<PlatformOpsFinding> findByIncidentIdOrderByCreatedAtDescIdDesc(Long incidentId, Pageable pageable);

    void deleteByRunIdAndSource(Long runId, PlatformOpsFindingSource source);

    long countByRunIdAndSource(Long runId, PlatformOpsFindingSource source);

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
}
