package com.archdox.cloud.configuration.infra;

import com.archdox.cloud.configuration.domain.OutputLayoutConfig;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutputLayoutConfigRepository extends JpaRepository<OutputLayoutConfig, Long> {
    @Query("""
            select c from OutputLayoutConfig c
            where (c.officeId = :officeId or c.officeId is null)
              and (:reportType is null or c.reportType = :reportType or c.reportType is null)
            order by case when c.officeId = :officeId then 0 else 1 end,
                     c.updatedAt desc,
                     c.id desc
            """)
    List<OutputLayoutConfig> findVisible(
            @Param("officeId") Long officeId,
            @Param("reportType") String reportType);
}
