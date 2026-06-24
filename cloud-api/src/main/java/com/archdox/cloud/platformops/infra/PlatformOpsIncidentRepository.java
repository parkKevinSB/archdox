package com.archdox.cloud.platformops.infra;

import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.domain.PlatformOpsIncident;
import com.archdox.cloud.platformops.domain.PlatformOpsIncidentStatus;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformOpsIncidentRepository extends JpaRepository<PlatformOpsIncident, Long> {
    Optional<PlatformOpsIncident> findFirstByStatusInAndCategoryAndPrimaryResourceTypeAndPrimaryResourceIdOrderByLastSeenAtDesc(
            Collection<PlatformOpsIncidentStatus> statuses,
            String category,
            String primaryResourceType,
            String primaryResourceId);

    long countByStatusIn(Collection<PlatformOpsIncidentStatus> statuses);

    List<PlatformOpsIncident> findByStatusInOrderByLastSeenAtDescIdDesc(
            Collection<PlatformOpsIncidentStatus> statuses,
            Pageable pageable);

    List<PlatformOpsIncident> findByStatusInAndCategoryOrderByLastSeenAtDescIdDesc(
            Collection<PlatformOpsIncidentStatus> statuses,
            String category,
            Pageable pageable);

    long countByStatusAndResolvedAtGreaterThanEqualAndResolvedAtLessThan(
            PlatformOpsIncidentStatus status,
            OffsetDateTime from,
            OffsetDateTime to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from PlatformOpsIncident incident
            where incident.lastSeenAt < :cutoff
              and not exists (
                  select 1
                  from PlatformOpsFinding finding
                  where finding.incidentId = incident.id
              )
            """)
    int deleteStaleUnreferencedBefore(@Param("cutoff") OffsetDateTime cutoff);

    @Query("""
            select incident
            from PlatformOpsIncident incident
            where (:officeId is null or incident.officeId = :officeId)
              and (:status is null or incident.status = :status)
              and (:severity is null or incident.severity = :severity)
              and (:category is null or incident.category = :category)
            order by incident.lastSeenAt desc, incident.id desc
            """)
    List<PlatformOpsIncident> search(
            @Param("officeId") Long officeId,
            @Param("status") PlatformOpsIncidentStatus status,
            @Param("severity") PlatformOpsFindingSeverity severity,
            @Param("category") String category,
            Pageable pageable);
}
