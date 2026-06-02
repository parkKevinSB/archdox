package com.archdox.cloud.reportai.application;

import com.archdox.cloud.inspection.application.ReportSubmitValidationService;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStatus;
import com.archdox.cloud.inspectiontarget.infra.InspectionReportTargetRepository;
import java.util.ArrayList;
import java.util.Locale;
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
        var policy = targetPolicy(report.reportType());
        if (policy.mode() == TargetRequirementMode.NONE) {
            return;
        }
        var targets = targetRepository.findByOfficeIdAndReportIdOrderByRoleAscIdAsc(report.officeId(), report.id());
        if (targets.isEmpty()) {
            findings.add(new ReportPreflightFinding(
                    "DETERMINISTIC",
                    "REPORT_TARGET_NOT_SELECTED",
                    policy.severity(),
                    "report.targets",
                    policy.message(),
                    "No inspection target is linked to the report",
                    Map.of(
                            "reportType", normalizeReportType(report.reportType()),
                            "targetPolicy", policy.mode().name())));
        }
    }

    private static TargetPolicy targetPolicy(String reportType) {
        return switch (normalizeReportType(reportType)) {
            case "DAILY_SUPERVISION",
                 "CONSTRUCTION_DAILY_LOG",
                 "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                 "CONSTRUCTION_SUPERVISION_DAILY_LOG" ->
                    new TargetPolicy(TargetRequirementMode.NONE, "NONE", "");
            case "DEMOLITION_SAFETY_CHECK",
                 "DEMOLITION_SAFETY_CHECKLIST" ->
                    new TargetPolicy(
                            TargetRequirementMode.REQUIRED,
                            "HIGH",
                            "해체공사 안전점검표는 해체 대상 구조물 또는 점검 대상을 연결해야 합니다.");
            case "PERIODIC_SAFETY",
                 "BUILDING_SAFETY_INSPECTION",
                 "SAFETY_INSPECTION_REPORT" ->
                    new TargetPolicy(
                            TargetRequirementMode.RECOMMENDED,
                            "LOW",
                            "안전점검 문서는 건축물 또는 대상 시설을 연결하는 것을 권장합니다.");
            default ->
                    new TargetPolicy(TargetRequirementMode.NONE, "NONE", "");
        };
    }

    private static String normalizeReportType(String reportType) {
        return reportType == null ? "" : reportType.trim().toUpperCase(Locale.ROOT);
    }

    private enum TargetRequirementMode {
        NONE,
        RECOMMENDED,
        REQUIRED
    }

    private record TargetPolicy(
            TargetRequirementMode mode,
            String severity,
            String message
    ) {
    }
}
