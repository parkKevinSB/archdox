package com.archdox.cloud.configuration.infra;

import com.archdox.cloud.configuration.domain.ConfigRevisionStatus;
import com.archdox.cloud.configuration.domain.WorkflowDefinitionRevision;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowDefinitionRevisionRepository extends JpaRepository<WorkflowDefinitionRevision, Long> {
    @EntityGraph(attributePaths = "definition")
    List<WorkflowDefinitionRevision> findByDefinitionIdOrderByVersionDesc(Long definitionId);

    @Query("""
            select coalesce(max(r.version), 0)
            from WorkflowDefinitionRevision r
            where r.definition.id = :definitionId
            """)
    int maxVersion(@Param("definitionId") Long definitionId);

    @EntityGraph(attributePaths = "definition")
    List<WorkflowDefinitionRevision> findByIdIn(List<Long> ids);

    @Query("""
            select r from WorkflowDefinitionRevision r
            join fetch r.definition d
            where d.officeId is null
              and r.status = :status
              and (:reportType is null or d.reportType = :reportType or d.reportType is null)
            order by case when d.reportType = :reportType then 0 else 1 end,
                     r.publishedAt desc,
                     r.version desc,
                     r.id desc
            """)
    List<WorkflowDefinitionRevision> findSystemPublishedCandidates(
            @Param("reportType") String reportType,
            @Param("status") ConfigRevisionStatus status,
            Pageable pageable);
}
