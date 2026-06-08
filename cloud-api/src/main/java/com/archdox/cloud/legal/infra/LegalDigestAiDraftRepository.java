package com.archdox.cloud.legal.infra;

import com.archdox.cloud.legal.domain.LegalDigestAiDraft;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalDigestAiDraftRepository extends JpaRepository<LegalDigestAiDraft, Long> {
    List<LegalDigestAiDraft> findByDigestIdOrderByGeneratedAtDescIdDesc(Long digestId);
}
