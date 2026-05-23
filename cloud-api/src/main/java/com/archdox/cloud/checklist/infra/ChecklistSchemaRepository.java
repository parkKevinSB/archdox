package com.archdox.cloud.checklist.infra;

import com.archdox.cloud.checklist.domain.ChecklistSchema;
import com.archdox.cloud.checklist.domain.ChecklistSchemaStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChecklistSchemaRepository extends JpaRepository<ChecklistSchema, Long> {
    List<ChecklistSchema> findByReportTypeAndStatusOrderByOfficeIdDescIdAsc(
            String reportType,
            ChecklistSchemaStatus status
    );
}
