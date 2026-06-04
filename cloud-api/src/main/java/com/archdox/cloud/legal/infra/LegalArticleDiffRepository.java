package com.archdox.cloud.legal.infra;

import com.archdox.cloud.legal.domain.LegalArticleDiff;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalArticleDiffRepository extends JpaRepository<LegalArticleDiff, Long> {
    List<LegalArticleDiff> findByChangeSetIdOrderByIdAsc(Long changeSetId);
}
