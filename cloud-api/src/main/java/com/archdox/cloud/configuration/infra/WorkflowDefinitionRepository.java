package com.archdox.cloud.configuration.infra;

import com.archdox.cloud.configuration.domain.WorkflowDefinition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, Long> {
    @Query("""
            select d from WorkflowDefinition d
            where (d.officeId = :officeId or d.officeId is null)
              and (:reportType is null or d.reportType = :reportType or d.reportType is null)
            order by case when d.officeId = :officeId then 0 else 1 end,
                     d.updatedAt desc,
                     d.id desc
            """)
    List<WorkflowDefinition> findVisible(
            @Param("officeId") Long officeId,
            @Param("reportType") String reportType);
}
