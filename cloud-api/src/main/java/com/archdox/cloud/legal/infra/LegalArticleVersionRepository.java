package com.archdox.cloud.legal.infra;

import com.archdox.cloud.legal.domain.LegalArticleVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalArticleVersionRepository extends JpaRepository<LegalArticleVersion, Long> {
    List<LegalArticleVersion> findByLegalVersionId(Long legalVersionId);

    Optional<LegalArticleVersion> findByArticleIdAndLegalVersionId(Long articleId, Long legalVersionId);
}
