package com.archdox.cloud.configuration.infra;

import com.archdox.cloud.configuration.domain.ConfigRevisionStatus;
import com.archdox.cloud.configuration.domain.OutputLayoutConfigRevision;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutputLayoutConfigRevisionRepository extends JpaRepository<OutputLayoutConfigRevision, Long> {
    @EntityGraph(attributePaths = "config")
    List<OutputLayoutConfigRevision> findByConfigIdOrderByVersionDesc(Long configId);

    @Query("""
            select coalesce(max(r.version), 0)
            from OutputLayoutConfigRevision r
            where r.config.id = :configId
            """)
    int maxVersion(@Param("configId") Long configId);

    @EntityGraph(attributePaths = "config")
    List<OutputLayoutConfigRevision> findByIdIn(List<Long> ids);

    @Query("""
            select r from OutputLayoutConfigRevision r
            join fetch r.config c
            where c.officeId is null
              and r.status = :status
              and (:reportType is null or c.reportType = :reportType or c.reportType is null)
            order by case when c.reportType = :reportType then 0 else 1 end,
                     r.publishedAt desc,
                     r.version desc,
                     r.id desc
            """)
    List<OutputLayoutConfigRevision> findSystemPublishedCandidates(
            @Param("reportType") String reportType,
            @Param("status") ConfigRevisionStatus status,
            Pageable pageable);
}
