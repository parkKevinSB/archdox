package com.archdox.cloud.assignment.application;

import com.archdox.cloud.assignment.domain.AssignmentStatus;
import com.archdox.cloud.assignment.domain.ProjectAssignment;
import com.archdox.cloud.assignment.domain.ReportAssignment;
import com.archdox.cloud.assignment.dto.ProjectAssignmentResponse;
import com.archdox.cloud.assignment.dto.ReportAssignmentResponse;
import com.archdox.cloud.assignment.dto.UpsertProjectAssignmentRequest;
import com.archdox.cloud.assignment.dto.UpsertReportAssignmentRequest;
import com.archdox.cloud.assignment.infra.ProjectAssignmentRepository;
import com.archdox.cloud.assignment.infra.ReportAssignmentRepository;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.inspection.application.InspectionReportService;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.application.OfficePermissionService;
import com.archdox.cloud.office.domain.OfficeMembership;
import com.archdox.cloud.office.infra.OfficeMembershipRepository;
import com.archdox.cloud.operation.application.OperationEventService;
import com.archdox.cloud.operation.domain.OperationEventSeverity;
import com.archdox.cloud.project.application.ProjectService;
import com.archdox.shared.MembershipStatus;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentService {
    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final ReportAssignmentRepository reportAssignmentRepository;
    private final OfficeMembershipRepository membershipRepository;
    private final ProjectService projectService;
    private final InspectionReportService reportService;
    private final OfficePermissionService permissionService;
    private final OperationEventService operationEventService;

    public AssignmentService(
            ProjectAssignmentRepository projectAssignmentRepository,
            ReportAssignmentRepository reportAssignmentRepository,
            OfficeMembershipRepository membershipRepository,
            ProjectService projectService,
            InspectionReportService reportService,
            OfficePermissionService permissionService,
            OperationEventService operationEventService
    ) {
        this.projectAssignmentRepository = projectAssignmentRepository;
        this.reportAssignmentRepository = reportAssignmentRepository;
        this.membershipRepository = membershipRepository;
        this.projectService = projectService;
        this.reportService = reportService;
        this.permissionService = permissionService;
        this.operationEventService = operationEventService;
    }

    @Transactional(readOnly = true)
    public java.util.List<ProjectAssignmentResponse> listProjectAssignments(Long projectId, UserPrincipal principal) {
        var project = projectService.requireProject(projectId);
        permissionService.requireActiveMembership(principal.userId(), project.officeId());
        var membershipsByUserId = activeMembershipsByUserId(project.officeId());
        return projectAssignmentRepository
                .findByOfficeIdAndProjectIdAndStatusOrderByAssignedAtDesc(
                        project.officeId(),
                        project.id(),
                        AssignmentStatus.ACTIVE)
                .stream()
                .map(assignment -> toProjectResponse(assignment, membershipsByUserId.get(assignment.userId())))
                .toList();
    }

    @Transactional
    public ProjectAssignmentResponse upsertProjectAssignment(
            Long projectId,
            UpsertProjectAssignmentRequest request,
            UserPrincipal principal
    ) {
        var project = projectService.requireProject(projectId);
        var officeId = project.officeId();
        permissionService.requireProjectManager(principal.userId(), officeId);
        var membership = requireActiveOfficeMember(request.userId(), officeId);
        var now = OffsetDateTime.now();
        var assignment = projectAssignmentRepository.findByOfficeIdAndProjectIdAndUserId(
                        officeId,
                        project.id(),
                        request.userId())
                .map(existing -> {
                    existing.update(request.role(), principal.userId(), now);
                    return existing;
                })
                .orElseGet(() -> projectAssignmentRepository.save(new ProjectAssignment(
                        officeId,
                        project.id(),
                        request.userId(),
                        request.role(),
                        principal.userId(),
                        now)));
        recordProjectAssignmentEvent(
                officeId,
                principal.userId(),
                "PROJECT_ASSIGNMENT_UPSERTED",
                assignment,
                Map.of("targetUserId", request.userId(), "role", request.role().name()));
        return toProjectResponse(assignment, membership);
    }

    @Transactional
    public ProjectAssignmentResponse removeProjectAssignment(
            Long projectId,
            Long userId,
            UserPrincipal principal
    ) {
        var project = projectService.requireProject(projectId);
        var officeId = project.officeId();
        permissionService.requireProjectManager(principal.userId(), officeId);
        var membership = requireActiveOfficeMember(userId, officeId);
        var assignment = projectAssignmentRepository.findByOfficeIdAndProjectIdAndUserId(
                        officeId,
                        project.id(),
                        userId)
                .orElseThrow(() -> new NotFoundException("Project assignment not found"));
        assignment.remove(principal.userId(), OffsetDateTime.now());
        recordProjectAssignmentEvent(
                officeId,
                principal.userId(),
                "PROJECT_ASSIGNMENT_REMOVED",
                assignment,
                Map.of("targetUserId", userId));
        return toProjectResponse(assignment, membership);
    }

    @Transactional(readOnly = true)
    public java.util.List<ReportAssignmentResponse> listReportAssignments(Long reportId, UserPrincipal principal) {
        var report = reportService.requireReport(reportId);
        permissionService.requireActiveMembership(principal.userId(), report.officeId());
        var membershipsByUserId = activeMembershipsByUserId(report.officeId());
        return reportAssignmentRepository
                .findByOfficeIdAndReportIdAndStatusOrderByAssignedAtDesc(
                        report.officeId(),
                        report.id(),
                        AssignmentStatus.ACTIVE)
                .stream()
                .map(assignment -> toReportResponse(assignment, membershipsByUserId.get(assignment.userId())))
                .toList();
    }

    @Transactional
    public ReportAssignmentResponse upsertReportAssignment(
            Long reportId,
            UpsertReportAssignmentRequest request,
            UserPrincipal principal
    ) {
        var report = reportService.requireReport(reportId);
        var officeId = report.officeId();
        permissionService.requireProjectManager(principal.userId(), officeId);
        var membership = requireActiveOfficeMember(request.userId(), officeId);
        var now = OffsetDateTime.now();
        var assignment = reportAssignmentRepository.findByOfficeIdAndReportIdAndUserId(
                        officeId,
                        report.id(),
                        request.userId())
                .map(existing -> {
                    existing.update(request.role(), principal.userId(), now);
                    return existing;
                })
                .orElseGet(() -> reportAssignmentRepository.save(new ReportAssignment(
                        officeId,
                        report.id(),
                        request.userId(),
                        request.role(),
                        principal.userId(),
                        now)));
        recordReportAssignmentEvent(
                officeId,
                principal.userId(),
                "REPORT_ASSIGNMENT_UPSERTED",
                assignment,
                Map.of("targetUserId", request.userId(), "role", request.role().name()));
        return toReportResponse(assignment, membership);
    }

    @Transactional
    public ReportAssignmentResponse removeReportAssignment(
            Long reportId,
            Long userId,
            UserPrincipal principal
    ) {
        var report = reportService.requireReport(reportId);
        var officeId = report.officeId();
        permissionService.requireProjectManager(principal.userId(), officeId);
        var membership = requireActiveOfficeMember(userId, officeId);
        var assignment = reportAssignmentRepository.findByOfficeIdAndReportIdAndUserId(
                        officeId,
                        report.id(),
                        userId)
                .orElseThrow(() -> new NotFoundException("Report assignment not found"));
        assignment.remove(principal.userId(), OffsetDateTime.now());
        recordReportAssignmentEvent(
                officeId,
                principal.userId(),
                "REPORT_ASSIGNMENT_REMOVED",
                assignment,
                Map.of("targetUserId", userId));
        return toReportResponse(assignment, membership);
    }

    private OfficeMembership requireActiveOfficeMember(Long userId, Long officeId) {
        return membershipRepository.findByUserIdAndOfficeIdAndStatus(userId, officeId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Office member not found"));
    }

    private Map<Long, OfficeMembership> activeMembershipsByUserId(Long officeId) {
        return membershipRepository.findByOfficeId(officeId).stream()
                .filter(membership -> membership.status() == MembershipStatus.ACTIVE)
                .collect(Collectors.toMap(membership -> membership.user().id(), Function.identity()));
    }

    private ProjectAssignmentResponse toProjectResponse(ProjectAssignment assignment, OfficeMembership membership) {
        return new ProjectAssignmentResponse(
                assignment.id(),
                assignment.officeId(),
                assignment.projectId(),
                assignment.userId(),
                membership == null ? null : membership.user().email(),
                membership == null ? null : membership.user().name(),
                assignment.role(),
                assignment.status(),
                assignment.assignedBy(),
                assignment.assignedAt(),
                assignment.updatedAt());
    }

    private ReportAssignmentResponse toReportResponse(ReportAssignment assignment, OfficeMembership membership) {
        return new ReportAssignmentResponse(
                assignment.id(),
                assignment.officeId(),
                assignment.reportId(),
                assignment.userId(),
                membership == null ? null : membership.user().email(),
                membership == null ? null : membership.user().name(),
                assignment.role(),
                assignment.status(),
                assignment.assignedBy(),
                assignment.assignedAt(),
                assignment.updatedAt());
    }

    private void recordProjectAssignmentEvent(
            Long officeId,
            Long actorUserId,
            String eventType,
            ProjectAssignment assignment,
            Map<String, Object> payload
    ) {
        operationEventService.record(
                officeId,
                OperationEventSeverity.INFO,
                eventType,
                null,
                null,
                "PROJECT_ASSIGNMENT",
                assignment.id(),
                actorUserId,
                null,
                eventType + " projectId=" + assignment.projectId() + " userId=" + assignment.userId(),
                payload);
    }

    private void recordReportAssignmentEvent(
            Long officeId,
            Long actorUserId,
            String eventType,
            ReportAssignment assignment,
            Map<String, Object> payload
    ) {
        operationEventService.record(
                officeId,
                OperationEventSeverity.INFO,
                eventType,
                null,
                null,
                "REPORT_ASSIGNMENT",
                assignment.id(),
                actorUserId,
                null,
                eventType + " reportId=" + assignment.reportId() + " userId=" + assignment.userId(),
                payload);
    }
}
