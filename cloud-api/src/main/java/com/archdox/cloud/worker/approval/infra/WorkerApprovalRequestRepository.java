package com.archdox.cloud.worker.approval.infra;

import com.archdox.cloud.worker.approval.domain.WorkerApprovalRequest;
import com.archdox.cloud.worker.approval.domain.WorkerApprovalRequestStatus;
import com.archdox.worker.domain.ArchDoxWorkerActionType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkerApprovalRequestRepository extends JpaRepository<WorkerApprovalRequest, Long> {
    Optional<WorkerApprovalRequest> findByWorkerRequestIdAndActionType(
            UUID workerRequestId,
            ArchDoxWorkerActionType actionType);

    @Query("""
            select request
            from WorkerApprovalRequest request
            where (:officeId is null or request.officeId = :officeId)
              and (:status is null or request.status = :status)
              and (:actionType is null or request.actionType = :actionType)
            order by request.requestedAt desc, request.id desc
            """)
    List<WorkerApprovalRequest> search(
            @Param("officeId") Long officeId,
            @Param("status") WorkerApprovalRequestStatus status,
            @Param("actionType") ArchDoxWorkerActionType actionType,
            Pageable pageable);
}
