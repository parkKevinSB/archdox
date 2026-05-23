package com.archdox.cloud.checklist.infra;

import com.archdox.cloud.checklist.domain.ChecklistSchema;
import com.archdox.cloud.checklist.domain.ChecklistSchemaStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChecklistSchemaRepository extends JpaRepository<ChecklistSchema, Long> {
    List<ChecklistSchema> findByReportTypeAndStatusOrderByOfficeIdDescIdAsc(
            String reportType,
            ChecklistSchemaStatus status
    );

    @Query("""
            select schema from ChecklistSchema schema
            where schema.reportType = :reportType
              and schema.status = :status
              and (schema.officeId is null or schema.officeId = :officeId)
              and (:siteType is null or schema.siteType is null or schema.siteType = :siteType)
              and (:targetType is null or schema.targetType is null or schema.targetType = :targetType)
            order by
              case when schema.officeId = :officeId then 0 else 1 end,
              case
                when :siteType is not null and schema.siteType = :siteType then 0
                when schema.siteType is null then 1
                else 2
              end,
              case
                when :targetType is not null and schema.targetType = :targetType then 0
                when schema.targetType is null then 1
                else 2
              end,
              schema.version desc,
              schema.id desc
            """)
    List<ChecklistSchema> findResolutionCandidates(
            @Param("officeId") Long officeId,
            @Param("reportType") String reportType,
            @Param("siteType") String siteType,
            @Param("targetType") String targetType,
            @Param("status") ChecklistSchemaStatus status,
            Pageable pageable
    );
}
