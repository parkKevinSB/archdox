package com.archdox.cloud.platformops.infra;

import com.archdox.cloud.platformops.domain.PlatformOpsDailyReport;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformOpsDailyReportRepository extends JpaRepository<PlatformOpsDailyReport, Long> {
    Optional<PlatformOpsDailyReport> findByRunId(Long runId);

    List<PlatformOpsDailyReport> findAllByOrderByDueAtDescIdDesc(Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from PlatformOpsDailyReport dailyReport
            where dailyReport.createdAt < :cutoff
            """)
    int deleteCreatedBefore(@Param("cutoff") OffsetDateTime cutoff);
}
