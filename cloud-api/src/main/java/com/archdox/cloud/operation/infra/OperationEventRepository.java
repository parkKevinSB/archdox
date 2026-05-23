package com.archdox.cloud.operation.infra;

import com.archdox.cloud.operation.domain.OperationEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OperationEventRepository extends JpaRepository<OperationEvent, Long> {
    @Query("""
            select event
            from OperationEvent event
            where event.officeId = :officeId
              and (:eventType is null or event.eventType = :eventType)
              and (:workflowType is null or event.workflowType = :workflowType)
              and (:workflowKey is null or event.workflowKey = :workflowKey)
              and (:resourceType is null or event.resourceType = :resourceType)
              and (:resourceId is null or event.resourceId = :resourceId)
            order by event.createdAt desc, event.id desc
            """)
    List<OperationEvent> searchOfficeEvents(
            Long officeId,
            String eventType,
            String workflowType,
            String workflowKey,
            String resourceType,
            String resourceId,
            Pageable pageable);
}
