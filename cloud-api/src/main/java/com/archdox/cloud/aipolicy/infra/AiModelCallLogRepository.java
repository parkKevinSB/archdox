package com.archdox.cloud.aipolicy.infra;

import com.archdox.cloud.aipolicy.domain.AiModelCallLog;
import com.archdox.cloud.aipolicy.domain.AiModelCallLogStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiModelCallLogRepository extends JpaRepository<AiModelCallLog, Long> {
    List<AiModelCallLog> findAllByOrderByCompletedAtDesc(Pageable pageable);

    List<AiModelCallLog> findByStatusOrderByCompletedAtDesc(AiModelCallLogStatus status, Pageable pageable);

    long countByOfficeIdAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
            Long officeId,
            OffsetDateTime from,
            OffsetDateTime to);

    @Query("""
            select coalesce(sum(coalesce(log.inputTokens, 0) + coalesce(log.outputTokens, 0)), 0)
            from AiModelCallLog log
            where log.officeId = :officeId
              and log.completedAt >= :from
              and log.completedAt < :to
            """)
    Long sumTokensByOfficeIdAndCompletedAtRange(
            @Param("officeId") Long officeId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Query("""
            select coalesce(sum(log.estimatedTotalCost), 0)
            from AiModelCallLog log
            where log.officeId = :officeId
              and log.costCurrency = :currency
              and log.completedAt >= :from
              and log.completedAt < :to
            """)
    BigDecimal sumEstimatedCostByOfficeIdAndCurrencyAndCompletedAtRange(
            @Param("officeId") Long officeId,
            @Param("currency") String currency,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Query("""
            select log.officeId as officeId,
                   coalesce(log.feature, 'UNKNOWN') as feature,
                   count(log.id) as callCount,
                   coalesce(sum(case when log.status = :succeeded then 1 else 0 end), 0) as succeededCount,
                   coalesce(sum(case when log.status = :failed then 1 else 0 end), 0) as failedCount,
                   coalesce(sum(log.inputTokens), 0) as inputTokens,
                   coalesce(sum(log.outputTokens), 0) as outputTokens,
                   coalesce(sum(log.estimatedTotalCost), 0) as estimatedTotalCost
            from AiModelCallLog log
            where log.completedAt >= :from
              and log.completedAt < :to
            group by log.officeId, log.feature
            order by log.officeId asc, log.feature asc
            """)
    List<AiUsageGroupProjection> usageByOfficeAndFeature(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("succeeded") AiModelCallLogStatus succeeded,
            @Param("failed") AiModelCallLogStatus failed);
}
