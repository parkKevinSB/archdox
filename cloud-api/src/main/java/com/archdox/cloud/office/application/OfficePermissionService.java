package com.archdox.cloud.office.application;

import com.archdox.cloud.assignment.domain.AssignmentStatus;
import com.archdox.cloud.assignment.domain.ProjectAssignmentRole;
import com.archdox.cloud.assignment.domain.ReportAssignmentRole;
import com.archdox.cloud.assignment.infra.ProjectAssignmentRepository;
import com.archdox.cloud.assignment.infra.ReportAssignmentRepository;
import com.archdox.cloud.global.api.ForbiddenException;
import com.archdox.cloud.global.api.UnauthorizedException;
import com.archdox.cloud.office.domain.OfficeMembership;
import com.archdox.cloud.office.infra.OfficeMembershipRepository;
import java.util.List;
import com.archdox.shared.MembershipRole;
import com.archdox.shared.MembershipStatus;
import com.archdox.shared.OfficeType;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OfficePermissionService {
    private final OfficeMembershipRepository membershipRepository;
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final ReportAssignmentRepository reportAssignmentRepository;

    public OfficePermissionService(
            OfficeMembershipRepository membershipRepository,
            ProjectAssignmentRepository projectAssignmentRepository,
            ReportAssignmentRepository reportAssignmentRepository
    ) {
        this.membershipRepository = membershipRepository;
        this.projectAssignmentRepository = projectAssignmentRepository;
        this.reportAssignmentRepository = reportAssignmentRepository;
    }

    public OfficeMembership requireActiveMembership(Long userId, Long officeId) {
        return membershipRepository.findByUserIdAndOfficeIdAndStatus(userId, officeId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new UnauthorizedException(
                        "OFFICE_MEMBERSHIP_REQUIRED",
                        "errors.office.membershipRequired",
                        "Office membership required",
                        Map.of("officeId", officeId)));
    }

    public void requireProjectManager(Long userId, Long officeId) {
        var membership = requireActiveMembership(userId, officeId);
        if (!canManageProjects(membership)) {
            throw new ForbiddenException(
                    "PROJECT_MANAGER_ROLE_REQUIRED",
                    "errors.project.managerRoleRequired",
                    "Project manager role required",
                    Map.of("officeId", officeId));
        }
    }

    public void requireProjectStructureManager(Long userId, Long officeId, Long projectId) {
        var membership = requireActiveMembership(userId, officeId);
        if (canManageProjects(membership)) {
            return;
        }
        if (membership.role() == MembershipRole.MEMBER
                && projectAssignmentRepository.existsByOfficeIdAndProjectIdAndUserIdAndStatusAndRoleIn(
                        officeId,
                        projectId,
                        userId,
                        AssignmentStatus.ACTIVE,
                        List.of(ProjectAssignmentRole.MANAGER))) {
            return;
        }
        throw new ForbiddenException(
                "PROJECT_MANAGER_ROLE_REQUIRED",
                "errors.project.managerRoleRequired",
                "Project manager role required",
                Map.of("officeId", officeId, "projectId", projectId));
    }

    public void requireReportWriter(Long userId, Long officeId) {
        var membership = requireActiveMembership(userId, officeId);
        if (!canWriteReports(membership)) {
            throw reportWriterForbidden(officeId, null, null);
        }
    }

    public void requireReportWriter(Long userId, Long officeId, Long projectId, Long reportId) {
        var membership = requireActiveMembership(userId, officeId);
        if (canManageProjects(membership)) {
            return;
        }
        if (membership.office().type() == OfficeType.PERSONAL) {
            if (membership.role() == MembershipRole.OWNER) {
                return;
            }
            throw reportWriterForbidden(officeId, projectId, reportId);
        }
        if (membership.role() != MembershipRole.MEMBER) {
            throw reportWriterForbidden(officeId, projectId, reportId);
        }

        if (reportId != null) {
            if (reportAssignmentRepository.existsByOfficeIdAndReportIdAndUserIdAndStatusAndRoleIn(
                    officeId,
                    reportId,
                    userId,
                    AssignmentStatus.ACTIVE,
                    List.of(ReportAssignmentRole.WRITER))) {
                return;
            }
            if (reportAssignmentRepository.existsByOfficeIdAndReportIdAndStatus(
                    officeId,
                    reportId,
                    AssignmentStatus.ACTIVE)) {
                throw new ForbiddenException(
                        "REPORT_ASSIGNMENT_REQUIRED",
                        "errors.report.assignmentRequired",
                        "Report assignment required",
                        Map.of("officeId", officeId, "projectId", projectId, "reportId", reportId));
            }
        }

        if (projectId != null) {
            if (projectAssignmentRepository.existsByOfficeIdAndProjectIdAndUserIdAndStatusAndRoleIn(
                    officeId,
                    projectId,
                    userId,
                    AssignmentStatus.ACTIVE,
                    List.of(ProjectAssignmentRole.MANAGER, ProjectAssignmentRole.REPORT_WRITER))) {
                return;
            }
            if (projectAssignmentRepository.existsByOfficeIdAndProjectIdAndStatus(
                    officeId,
                    projectId,
                    AssignmentStatus.ACTIVE)) {
                throw new ForbiddenException(
                        "PROJECT_ASSIGNMENT_REQUIRED",
                        "errors.project.assignmentRequired",
                        "Project assignment required",
                        Map.of("officeId", officeId, "projectId", projectId));
            }
        }
    }

    public boolean canWriteReport(Long userId, Long officeId, Long projectId, Long reportId) {
        try {
            requireReportWriter(userId, officeId, projectId, reportId);
            return true;
        } catch (ForbiddenException | UnauthorizedException ex) {
            return false;
        }
    }

    public boolean canManageProjects(OfficeMembership membership) {
        if (membership.office().type() == OfficeType.PERSONAL) {
            return membership.role() == MembershipRole.OWNER;
        }
        return membership.role() == MembershipRole.OWNER || membership.role() == MembershipRole.ADMIN;
    }

    public boolean canWriteReports(OfficeMembership membership) {
        if (membership.office().type() == OfficeType.PERSONAL) {
            return membership.role() == MembershipRole.OWNER;
        }
        return membership.role() == MembershipRole.OWNER
                || membership.role() == MembershipRole.ADMIN
                || membership.role() == MembershipRole.MEMBER;
    }

    private ForbiddenException reportWriterForbidden(Long officeId, Long projectId, Long reportId) {
        var params = new java.util.LinkedHashMap<String, Object>();
        params.put("officeId", officeId);
        if (projectId != null) {
            params.put("projectId", projectId);
        }
        if (reportId != null) {
            params.put("reportId", reportId);
        }
        return new ForbiddenException(
                "REPORT_WRITE_FORBIDDEN",
                "errors.report.writeForbidden",
                "Report writer role required",
                params);
    }
}
