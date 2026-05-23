package com.archdox.cloud.configuration.infra;

import com.archdox.cloud.configuration.domain.ConfigRevisionStatus;
import com.archdox.cloud.configuration.domain.DocumentTemplateRevision;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentTemplateRevisionRepository extends JpaRepository<DocumentTemplateRevision, Long> {
    @EntityGraph(attributePaths = "template")
    List<DocumentTemplateRevision> findByTemplateIdOrderByVersionDesc(Long templateId);

    @Query("""
            select coalesce(max(r.version), 0)
            from DocumentTemplateRevision r
            where r.template.id = :templateId
            """)
    int maxVersion(@Param("templateId") Long templateId);

    @EntityGraph(attributePaths = "template")
    List<DocumentTemplateRevision> findByIdIn(List<Long> ids);

    @Query("""
            select r from DocumentTemplateRevision r
            join fetch r.template t
            where t.officeId is null
              and r.status = :status
              and (:reportType is null or t.reportType = :reportType or t.reportType is null)
            order by case when t.reportType = :reportType then 0 else 1 end,
                     r.publishedAt desc,
                     r.version desc,
                     r.id desc
            """)
    List<DocumentTemplateRevision> findSystemPublishedCandidates(
            @Param("reportType") String reportType,
            @Param("status") ConfigRevisionStatus status,
            Pageable pageable);
}
