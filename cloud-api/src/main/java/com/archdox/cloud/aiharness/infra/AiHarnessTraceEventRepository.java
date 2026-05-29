package com.archdox.cloud.aiharness.infra;

import com.archdox.cloud.aiharness.domain.AiHarnessTraceEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiHarnessTraceEventRepository extends JpaRepository<AiHarnessTraceEvent, Long> {
    List<AiHarnessTraceEvent> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    List<AiHarnessTraceEvent> findByHarnessRunIdOrderByCreatedAtAscIdAsc(String harnessRunId, Pageable pageable);

    List<AiHarnessTraceEvent> findByHarnessIdOrderByCreatedAtDescIdDesc(String harnessId, Pageable pageable);

    List<AiHarnessTraceEvent> findByEventTypeOrderByCreatedAtDescIdDesc(String eventType, Pageable pageable);
}
