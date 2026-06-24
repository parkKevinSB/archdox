package com.archdox.cloud.aiharness.infra;

import com.archdox.cloud.aiharness.domain.AiHarnessTraceEvent;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiHarnessTraceEventRepository extends JpaRepository<AiHarnessTraceEvent, Long> {
    List<AiHarnessTraceEvent> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from AiHarnessTraceEvent event
            where event.createdAt < :cutoff
            """)
    int deleteCreatedBefore(@Param("cutoff") OffsetDateTime cutoff);

    List<AiHarnessTraceEvent> findByHarnessRunIdOrderByCreatedAtAscIdAsc(String harnessRunId, Pageable pageable);

    List<AiHarnessTraceEvent> findByHarnessIdOrderByCreatedAtDescIdDesc(String harnessId, Pageable pageable);

    List<AiHarnessTraceEvent> findByEventTypeOrderByCreatedAtDescIdDesc(String eventType, Pageable pageable);
}
