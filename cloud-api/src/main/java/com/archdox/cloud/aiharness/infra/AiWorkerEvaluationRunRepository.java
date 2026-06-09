package com.archdox.cloud.aiharness.infra;

import com.archdox.cloud.aiharness.domain.AiWorkerEvaluationRun;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AiWorkerEvaluationRunRepository extends JpaRepository<AiWorkerEvaluationRun, Long> {
    List<AiWorkerEvaluationRun> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            delete from ai_worker_evaluation_runs
            where id not in (
                select id
                from ai_worker_evaluation_runs
                order by created_at desc, id desc
                limit :retainedCount
            )
            """, nativeQuery = true)
    int deleteAllButMostRecent(@Param("retainedCount") int retainedCount);
}
