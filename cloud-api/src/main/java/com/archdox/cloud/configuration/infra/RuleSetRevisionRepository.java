package com.archdox.cloud.configuration.infra;

import com.archdox.cloud.configuration.domain.ConfigRevisionStatus;
import com.archdox.cloud.configuration.domain.RuleSetRevision;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RuleSetRevisionRepository extends JpaRepository<RuleSetRevision, Long> {
    @EntityGraph(attributePaths = "ruleSet")
    List<RuleSetRevision> findByRuleSetIdOrderByVersionDesc(Long ruleSetId);

    @Query("""
            select coalesce(max(r.version), 0)
            from RuleSetRevision r
            where r.ruleSet.id = :ruleSetId
            """)
    int maxVersion(@Param("ruleSetId") Long ruleSetId);

    @EntityGraph(attributePaths = "ruleSet")
    List<RuleSetRevision> findByIdIn(List<Long> ids);

    @Query("""
            select r from RuleSetRevision r
            join fetch r.ruleSet s
            where s.officeId is null
              and r.status = :status
              and (:reportType is null or s.reportType = :reportType or s.reportType is null)
            order by case when s.reportType = :reportType then 0 else 1 end,
                     r.publishedAt desc,
                     r.version desc,
                     r.id desc
            """)
    List<RuleSetRevision> findSystemPublishedCandidates(
            @Param("reportType") String reportType,
            @Param("status") ConfigRevisionStatus status,
            Pageable pageable);
}
