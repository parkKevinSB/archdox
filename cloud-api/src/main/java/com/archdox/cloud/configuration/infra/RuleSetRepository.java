package com.archdox.cloud.configuration.infra;

import com.archdox.cloud.configuration.domain.RuleSet;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RuleSetRepository extends JpaRepository<RuleSet, Long> {
    @Query("""
            select s from RuleSet s
            where (s.officeId = :officeId or s.officeId is null)
              and (:reportType is null or s.reportType = :reportType or s.reportType is null)
            order by case when s.officeId = :officeId then 0 else 1 end,
                     s.updatedAt desc,
                     s.id desc
            """)
    List<RuleSet> findVisible(
            @Param("officeId") Long officeId,
            @Param("reportType") String reportType);
}
