package com.archdox.cloud.reportai.application;

import com.archdox.cloud.inspection.application.ReportSubmitValidationService;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStatus;
import com.archdox.cloud.inspectiontarget.infra.InspectionReportTargetRepository;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ReportPreflightDeterministicValidator {
    private final ReportSubmitValidationService submitValidationService;
    private final InspectionReportTargetRepository targetRepository;

    public ReportPreflightDeterministicValidator(
            ReportSubmitValidationService submitValidationService,
            InspectionReportTargetRepository targetRepository
    ) {
        this.submitValidationService = submitValidationService;
        this.targetRepository = targetRepository;
    }

    public ReportPreflightValidationResult validate(InspectionReport report) {
        var findings = new ArrayList<ReportPreflightFinding>();
        validateReportState(report, findings);
        var submitValidation = submitValidationService.validate(report);
        for (var issue : submitValidation.blockingIssues()) {
            findings.add(new ReportPreflightFinding(
                    "DETERMINISTIC",
                    issue.code(),
                    "HIGH",
                    issue.resourceKey(),
                    issue.message(),
                    issue.resourceType(),
                    Map.of("resourceType", issue.resourceType())));
        }
        for (var warning : submitValidation.warnings()) {
            findings.add(new ReportPreflightFinding(
                    "DETERMINISTIC",
                    warning.code(),
                    "LOW",
                    warning.resourceKey(),
                    warning.message(),
                    warning.resourceType(),
                    Map.of("resourceType", warning.resourceType())));
        }
        validateTargetPresence(report, findings);
        return new ReportPreflightValidationResult(findings);
    }

    private void validateReportState(InspectionReport report, ArrayList<ReportPreflightFinding> findings) {
        if (report.status() == InspectionReportStatus.CANCELLED) {
            findings.add(new ReportPreflightFinding(
                    "DETERMINISTIC",
                    "REPORT_CANCELLED",
                    "HIGH",
                    "report.status",
                    "취소된 리포트는 문서 생성 전 검토를 진행할 수 없습니다.",
                    "status=" + report.status().name(),
                    Map.of("status", report.status().name())));
        }
        if (report.status() == InspectionReportStatus.GENERATION_REQUESTED
                || report.status() == InspectionReportStatus.GENERATING) {
            findings.add(new ReportPreflightFinding(
                    "DETERMINISTIC",
                    "REPORT_GENERATION_IN_PROGRESS",
                    "HIGH",
                    "report.status",
                    "문서 생성이 진행 중인 리포트는 사전 검토를 다시 시작할 수 없습니다.",
                    "status=" + report.status().name(),
                    Map.of("status", report.status().name())));
        }
    }

    private void validateTargetPresence(InspectionReport report, ArrayList<ReportPreflightFinding> findings) {
        var targets = targetRepository.findByOfficeIdAndReportIdOrderByRoleAscIdAsc(report.officeId(), report.id());
        if (targets.isEmpty()) {
            findings.add(new ReportPreflightFinding(
                    "DETERMINISTIC",
                    "REPORT_TARGET_NOT_SELECTED",
                    "LOW",
                    "report.targets",
                    "점검 대상이 연결되지 않았습니다. 문서유형에 따라 대상 연결이 필요할 수 있습니다.",
                    "No inspection target is linked to the report",
                    Map.of()));
        }
    }
}
