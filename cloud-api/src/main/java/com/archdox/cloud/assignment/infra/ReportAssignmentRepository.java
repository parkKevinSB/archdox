package com.archdox.cloud.assignment.infra;

import com.archdox.cloud.assignment.domain.AssignmentStatus;
import com.archdox.cloud.assignment.domain.ReportAssignment;
import com.archdox.cloud.assignment.domain.ReportAssignmentRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportAssignmentRepository extends JpaRepository<ReportAssignment, Long> {
    List<ReportAssignment> findByOfficeIdAndReportIdAndStatusOrderByAssignedAtDesc(
            Long officeId,
            Long reportId,
            AssignmentStatus status);

    Optional<ReportAssignment> findByOfficeIdAndReportIdAndUserId(Long officeId, Long reportId, Long userId);

    boolean existsByOfficeIdAndReportIdAndStatus(Long officeId, Long reportId, AssignmentStatus status);

    boolean existsByOfficeIdAndReportIdAndUserIdAndStatusAndRoleIn(
            Long officeId,
            Long reportId,
            Long userId,
            AssignmentStatus status,
            Collection<ReportAssignmentRole> roles);
}
