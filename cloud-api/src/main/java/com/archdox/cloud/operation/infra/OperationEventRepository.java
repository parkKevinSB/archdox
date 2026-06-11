package com.archdox.cloud.operation.infra;

import com.archdox.cloud.operation.domain.OperationEvent;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationEventRepository extends JpaRepository<OperationEvent, Long> {
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(OffsetDateTime from, OffsetDateTime to);

    List<OperationEvent> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable);

    @Query("""
            select event.severity as severity,
                   count(event.id) as eventCount
            from OperationEvent event
            where event.createdAt >= :from
              and event.createdAt < :to
            group by event.severity
            order by count(event.id) desc
            """)
    List<OperationEventSeverityCountProjection> summarizeSeverity(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

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

    @Query("""
            select event
            from OperationEvent event
            where (:officeId is null or event.officeId = :officeId)
              and (:eventType is null or event.eventType = :eventType)
              and (:workflowType is null or event.workflowType = :workflowType)
              and (:workflowKey is null or event.workflowKey = :workflowKey)
              and (:resourceType is null or event.resourceType = :resourceType)
              and (:resourceId is null or event.resourceId = :resourceId)
            order by event.createdAt desc, event.id desc
            """)
    List<OperationEvent> searchPlatformEvents(
            Long officeId,
            String eventType,
            String workflowType,
            String workflowKey,
            String resourceType,
            String resourceId,
            Pageable pageable);

    @Query(value = """
            select event_type as eventType,
                   count(*) as eventCount
            from operation_events
            where workflow_type = 'archdox-worker'
              and (:officeId is null or office_id = :officeId)
              and created_at >= :from
              and created_at < :to
            group by event_type
            order by count(*) desc
            """, nativeQuery = true)
    List<WorkerEventTypeCountProjection> summarizeWorkerEventTypes(
            @Param("officeId") Long officeId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Query(value = """
            select coalesce(payload_json ->> 'actionType', 'UNKNOWN') as actionType,
                   event_type as eventType,
                   count(*) as eventCount
            from operation_events
            where workflow_type = 'archdox-worker'
              and (:officeId is null or office_id = :officeId)
              and created_at >= :from
              and created_at < :to
            group by coalesce(payload_json ->> 'actionType', 'UNKNOWN'), event_type
            order by count(*) desc
            """, nativeQuery = true)
    List<WorkerActionEventCountProjection> summarizeWorkerActionEvents(
            @Param("officeId") Long officeId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    @Query(value = """
            select event_type as eventType,
                   coalesce(payload_json ->> 'code', 'UNKNOWN') as reasonCode,
                   count(*) as eventCount
            from operation_events
            where workflow_type = 'archdox-worker'
              and event_type in (
                  'ARCHDOX_WORKER_POLICY_DENIED',
                  'ARCHDOX_WORKER_APPROVAL_REQUIRED',
                  'ARCHDOX_WORKER_ACTION_REJECTED',
                  'ARCHDOX_WORKER_ACTION_CANCELLED',
                  'ARCHDOX_WORKER_ACTION_FAILED',
                  'ARCHDOX_WORKER_ACTION_UNKNOWN'
              )
              and (:officeId is null or office_id = :officeId)
              and created_at >= :from
              and created_at < :to
            group by event_type, coalesce(payload_json ->> 'code', 'UNKNOWN')
            order by count(*) desc
            """, nativeQuery = true)
    List<WorkerReasonCountProjection> summarizeWorkerReasons(
            @Param("officeId") Long officeId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);

    interface WorkerEventTypeCountProjection {
        String getEventType();

        Long getEventCount();
    }

    interface WorkerActionEventCountProjection {
        String getActionType();

        String getEventType();

        Long getEventCount();
    }

    interface WorkerReasonCountProjection {
        String getEventType();

        String getReasonCode();

        Long getEventCount();
    }

    interface OperationEventSeverityCountProjection {
        com.archdox.cloud.operation.domain.OperationEventSeverity getSeverity();

        Long getEventCount();
    }
}
