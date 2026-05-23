package com.archdox.cloud.configuration.infra;

import com.archdox.cloud.configuration.domain.DocumentTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, Long> {
    @Query("""
            select t from DocumentTemplate t
            where (t.officeId = :officeId or t.officeId is null)
              and (:reportType is null or t.reportType = :reportType or t.reportType is null)
            order by case when t.officeId = :officeId then 0 else 1 end,
                     t.updatedAt desc,
                     t.id desc
            """)
    List<DocumentTemplate> findVisible(
            @Param("officeId") Long officeId,
            @Param("reportType") String reportType);
}
