package com.archdox.cloud.reportai.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.inspection.application.ReportSubmitValidationService;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.dto.ReportSubmitValidationIssueResponse;
import com.archdox.cloud.inspection.dto.ReportSubmitValidationResponse;
import com.archdox.cloud.inspectiontarget.infra.InspectionReportTargetRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReportPreflightDeterministicValidatorTest {
    @Test
    void submitValidationBlockingIssueBlocksGeneration() {
        var submitValidationService = mock(ReportSubmitValidationService.class);
        var targetRepository = mock(InspectionReportTargetRepository.class);
        var report = report();
        when(submitValidationService.validate(report)).thenReturn(ReportSubmitValidationResponse.invalid(
                List.of(new ReportSubmitValidationIssueResponse(
                        "MISSING_STEP_BASIC_INFO",
                        "Basic info step must be saved before submit.",
                        "INSPECTION_REPORT_STEP",
                        "BASIC_INFO")),
                List.of()));
        when(targetRepository.findByOfficeIdAndReportIdOrderByRoleAscIdAsc(10L, 100L)).thenReturn(List.of());

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository)
                .validate(report);

        assertTrue(result.blocksGeneration());
        assertTrue(result.findings().stream().anyMatch(finding -> "MISSING_STEP_BASIC_INFO".equals(finding.code())));
    }

    @Test
    void targetMissingIsWarningOnly() {
        var submitValidationService = mock(ReportSubmitValidationService.class);
        var targetRepository = mock(InspectionReportTargetRepository.class);
        var report = report("PERIODIC_SAFETY");
        when(submitValidationService.validate(report)).thenReturn(ReportSubmitValidationResponse.valid(List.of()));
        when(targetRepository.findByOfficeIdAndReportIdOrderByRoleAscIdAsc(10L, 100L)).thenReturn(List.of());

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository)
                .validate(report);

        assertFalse(result.blocksGeneration());
        assertTrue(result.findings().stream().anyMatch(finding -> "REPORT_TARGET_NOT_SELECTED".equals(finding.code())));
    }

    @Test
    void dailySupervisionDoesNotWarnWhenTargetIsMissing() {
        var submitValidationService = mock(ReportSubmitValidationService.class);
        var targetRepository = mock(InspectionReportTargetRepository.class);
        var report = report("CONSTRUCTION_SUPERVISION_DAILY_LOG");
        when(submitValidationService.validate(report)).thenReturn(ReportSubmitValidationResponse.valid(List.of()));

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository)
                .validate(report);

        assertFalse(result.blocksGeneration());
        assertFalse(result.findings().stream().anyMatch(finding -> "REPORT_TARGET_NOT_SELECTED".equals(finding.code())));
        verify(targetRepository, never()).findByOfficeIdAndReportIdOrderByRoleAscIdAsc(10L, 100L);
    }

    @Test
    void demolitionSafetyChecklistRequiresTarget() {
        var submitValidationService = mock(ReportSubmitValidationService.class);
        var targetRepository = mock(InspectionReportTargetRepository.class);
        var report = report("DEMOLITION_SAFETY_CHECKLIST");
        when(submitValidationService.validate(report)).thenReturn(ReportSubmitValidationResponse.valid(List.of()));
        when(targetRepository.findByOfficeIdAndReportIdOrderByRoleAscIdAsc(10L, 100L)).thenReturn(List.of());

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository)
                .validate(report);

        assertTrue(result.blocksGeneration());
        assertTrue(result.findings().stream().anyMatch(finding ->
                "REPORT_TARGET_NOT_SELECTED".equals(finding.code()) && "HIGH".equals(finding.severity())));
    }

    private InspectionReport report() {
        return report("CONSTRUCTION_SUPERVISION_DAILY_LOG");
    }

    private InspectionReport report(String reportType) {
        var report = new InspectionReport(
                10L,
                20L,
                30L,
                "R-001",
                reportType,
                "공사감리일지",
                40L,
                50L,
                OffsetDateTime.now());
        ReflectionTestUtils.setField(report, "id", 100L);
        return report;
    }
}
