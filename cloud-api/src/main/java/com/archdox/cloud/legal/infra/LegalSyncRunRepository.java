package com.archdox.cloud.legal.infra;

import com.archdox.cloud.legal.domain.LegalSyncRun;
import com.archdox.cloud.legal.domain.LegalSyncRunStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalSyncRunRepository extends JpaRepository<LegalSyncRun, Long> {
    List<LegalSyncRun> findBySourceCodeOrderByStartedAtDescIdDesc(String sourceCode, Pageable pageable);

    List<LegalSyncRun> findAllByOrderByStartedAtDescIdDesc(Pageable pageable);

    boolean existsBySourceCodeAndStatus(String sourceCode, LegalSyncRunStatus status);

    Optional<LegalSyncRun> findFirstBySourceCodeOrderByStartedAtDescIdDesc(String sourceCode);
}
