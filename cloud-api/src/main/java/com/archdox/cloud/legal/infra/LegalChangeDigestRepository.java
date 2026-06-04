package com.archdox.cloud.legal.infra;

import com.archdox.cloud.legal.domain.LegalChangeDigest;
import com.archdox.cloud.legal.domain.LegalChangeDigestStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LegalChangeDigestRepository extends JpaRepository<LegalChangeDigest, Long> {
    Optional<LegalChangeDigest> findByChangeSetId(Long changeSetId);

    List<LegalChangeDigest> findAllByOrderByDetectedAtDescIdDesc(Pageable pageable);

    List<LegalChangeDigest> findByStatusAndPublishedAtAfterOrderByPublishedAtDescIdDesc(
            LegalChangeDigestStatus status,
            OffsetDateTime publishedAfter,
            Pageable pageable
    );
}
