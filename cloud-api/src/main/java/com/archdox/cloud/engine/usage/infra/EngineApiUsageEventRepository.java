package com.archdox.cloud.engine.usage.infra;

import com.archdox.cloud.engine.usage.domain.EngineApiUsageEvent;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EngineApiUsageEventRepository extends JpaRepository<EngineApiUsageEvent, Long> {
    @Query("""
            select e
            from EngineApiUsageEvent e
            where (:apiKeyId is null or e.apiKeyId = :apiKeyId)
              and (:officeId is null or e.officeId = :officeId)
              and (:capability is null or e.capability = :capability)
              and (:operation is null or e.operation = :operation)
              and (:reviewSessionId is null or e.reviewSessionId = :reviewSessionId)
              and e.createdAt >= :from
              and e.createdAt < :to
            order by e.createdAt desc, e.id desc
            """)
    List<EngineApiUsageEvent> searchPlatformUsageEvents(
            @Param("apiKeyId") Long apiKeyId,
            @Param("officeId") Long officeId,
            @Param("capability") String capability,
            @Param("operation") String operation,
            @Param("reviewSessionId") String reviewSessionId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);

    @Query("""
            select e
            from EngineApiUsageEvent e
            where e.ownerUserId = :ownerUserId
              and (:apiKeyId is null or e.apiKeyId = :apiKeyId)
              and (:officeId is null or e.officeId = :officeId)
              and (:capability is null or e.capability = :capability)
              and (:operation is null or e.operation = :operation)
              and (:reviewSessionId is null or e.reviewSessionId = :reviewSessionId)
              and e.createdAt >= :from
              and e.createdAt < :to
            order by e.createdAt desc, e.id desc
            """)
    List<EngineApiUsageEvent> searchUserUsageEvents(
            @Param("ownerUserId") Long ownerUserId,
            @Param("apiKeyId") Long apiKeyId,
            @Param("officeId") Long officeId,
            @Param("capability") String capability,
            @Param("operation") String operation,
            @Param("reviewSessionId") String reviewSessionId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);

    @Query("""
            select
              e.apiKeyId as apiKeyId,
              e.keyId as keyId,
              e.ownerUserId as ownerUserId,
              e.officeId as officeId,
              e.capability as capability,
              e.operation as operation,
              count(e) as eventCount,
              coalesce(sum(e.requestUnits), 0) as requestUnits,
              max(e.createdAt) as lastCalledAt
            from EngineApiUsageEvent e
            where (:apiKeyId is null or e.apiKeyId = :apiKeyId)
              and (:officeId is null or e.officeId = :officeId)
              and (:capability is null or e.capability = :capability)
              and (:operation is null or e.operation = :operation)
              and e.createdAt >= :from
              and e.createdAt < :to
            group by e.apiKeyId, e.keyId, e.ownerUserId, e.officeId, e.capability, e.operation
            order by max(e.createdAt) desc
            """)
    List<EngineApiUsageSummaryProjection> summarizePlatformUsage(
            @Param("apiKeyId") Long apiKeyId,
            @Param("officeId") Long officeId,
            @Param("capability") String capability,
            @Param("operation") String operation,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Query("""
            select
              e.apiKeyId as apiKeyId,
              e.keyId as keyId,
              e.ownerUserId as ownerUserId,
              e.officeId as officeId,
              e.capability as capability,
              e.operation as operation,
              count(e) as eventCount,
              coalesce(sum(e.requestUnits), 0) as requestUnits,
              max(e.createdAt) as lastCalledAt
            from EngineApiUsageEvent e
            where e.ownerUserId = :ownerUserId
              and (:apiKeyId is null or e.apiKeyId = :apiKeyId)
              and (:officeId is null or e.officeId = :officeId)
              and (:capability is null or e.capability = :capability)
              and (:operation is null or e.operation = :operation)
              and e.createdAt >= :from
              and e.createdAt < :to
            group by e.apiKeyId, e.keyId, e.ownerUserId, e.officeId, e.capability, e.operation
            order by max(e.createdAt) desc
            """)
    List<EngineApiUsageSummaryProjection> summarizeUserUsage(
            @Param("ownerUserId") Long ownerUserId,
            @Param("apiKeyId") Long apiKeyId,
            @Param("officeId") Long officeId,
            @Param("capability") String capability,
            @Param("operation") String operation,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Query("""
            select coalesce(sum(e.requestUnits), 0)
            from EngineApiUsageEvent e
            where e.apiKeyId = :apiKeyId
              and e.capability = :capability
              and e.createdAt >= :from
              and e.createdAt < :to
            """)
    Long sumRequestUnitsForApiKey(
            @Param("apiKeyId") Long apiKeyId,
            @Param("capability") String capability,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    interface EngineApiUsageSummaryProjection {
        Long getApiKeyId();

        String getKeyId();

        Long getOwnerUserId();

        Long getOfficeId();

        String getCapability();

        String getOperation();

        Long getEventCount();

        Long getRequestUnits();

        OffsetDateTime getLastCalledAt();
    }
}
