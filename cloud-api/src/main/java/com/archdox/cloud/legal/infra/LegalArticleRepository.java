package com.archdox.cloud.legal.infra;

import com.archdox.cloud.legal.domain.LegalArticle;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalArticleRepository extends JpaRepository<LegalArticle, Long> {
    Optional<LegalArticle> findByActIdAndArticleKey(Long actId, String articleKey);
}
