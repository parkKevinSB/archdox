package com.archdox.cloud.legal.infra;

import com.archdox.cloud.legal.domain.LegalChangeDigest;
import com.archdox.cloud.legal.domain.LegalChangeDigestStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LegalChangeDigestRepository extends JpaRepository<LegalChangeDigest, Long> {
    Optional<LegalChangeDigest> findByChangeSetId(Long changeSetId);

    List<LegalChangeDigest> findAllByOrderByDetectedAtDescIdDesc(Pageable pageable);

    List<LegalChangeDigest> findByStatusAndPublishedAtAfterOrderByPublishedAtDescIdDesc(
            LegalChangeDigestStatus status,
            OffsetDateTime publishedAfter,
            Pageable pageable
    );

    @Query("""
            select digest
            from LegalChangeDigest digest
            where digest.status = :status
              and digest.publishedAt > :publishedAfter
              and not exists (
                  select 1
                  from LegalChangeSet changeSet, LegalSyncRun syncRun
                  where changeSet.id = digest.changeSetId
                    and syncRun.id = changeSet.syncRunId
                    and syncRun.sourceCode = :excludedSourceCode
              )
            order by digest.publishedAt desc, digest.id desc
            """)
    List<LegalChangeDigest> findPublishedExcludingSourceCode(
            @Param("status") LegalChangeDigestStatus status,
            @Param("publishedAfter") OffsetDateTime publishedAfter,
            @Param("excludedSourceCode") String excludedSourceCode,
            Pageable pageable
    );

    @Query("""
            select digest
            from LegalChangeDigest digest
            where digest.id = :id
              and digest.status = :status
              and not exists (
                  select 1
                  from LegalChangeSet changeSet, LegalSyncRun syncRun
                  where changeSet.id = digest.changeSetId
                    and syncRun.id = changeSet.syncRunId
                    and syncRun.sourceCode = :excludedSourceCode
              )
            """)
    Optional<LegalChangeDigest> findPublishedByIdExcludingSourceCode(
            @Param("id") Long id,
            @Param("status") LegalChangeDigestStatus status,
            @Param("excludedSourceCode") String excludedSourceCode
    );
}
