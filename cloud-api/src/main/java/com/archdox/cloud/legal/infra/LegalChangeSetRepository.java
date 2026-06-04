package com.archdox.cloud.legal.infra;

import com.archdox.cloud.legal.domain.LegalChangeSet;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalChangeSetRepository extends JpaRepository<LegalChangeSet, Long> {
    List<LegalChangeSet> findAllByOrderByDetectedAtDescIdDesc(Pageable pageable);

    List<LegalChangeSet> findBySyncRunIdOrderByIdAsc(Long syncRunId);
}
