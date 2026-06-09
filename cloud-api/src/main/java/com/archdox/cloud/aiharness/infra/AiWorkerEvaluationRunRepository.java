package com.archdox.cloud.aiharness.infra;

import com.archdox.cloud.aiharness.domain.AiWorkerEvaluationRun;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiWorkerEvaluationRunRepository extends JpaRepository<AiWorkerEvaluationRun, Long> {
    List<AiWorkerEvaluationRun> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
