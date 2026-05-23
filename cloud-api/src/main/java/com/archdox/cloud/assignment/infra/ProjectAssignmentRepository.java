package com.archdox.cloud.assignment.infra;

import com.archdox.cloud.assignment.domain.AssignmentStatus;
import com.archdox.cloud.assignment.domain.ProjectAssignment;
import com.archdox.cloud.assignment.domain.ProjectAssignmentRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectAssignmentRepository extends JpaRepository<ProjectAssignment, Long> {
    List<ProjectAssignment> findByOfficeIdAndProjectIdAndStatusOrderByAssignedAtDesc(
            Long officeId,
            Long projectId,
            AssignmentStatus status);

    Optional<ProjectAssignment> findByOfficeIdAndProjectIdAndUserId(Long officeId, Long projectId, Long userId);

    boolean existsByOfficeIdAndProjectIdAndStatus(Long officeId, Long projectId, AssignmentStatus status);

    boolean existsByOfficeIdAndProjectIdAndUserIdAndStatusAndRoleIn(
            Long officeId,
            Long projectId,
            Long userId,
            AssignmentStatus status,
            Collection<ProjectAssignmentRole> roles);
}
