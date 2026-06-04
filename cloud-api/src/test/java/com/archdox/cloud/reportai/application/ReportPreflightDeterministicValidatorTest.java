package com.archdox.cloud.reportai.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.inspection.application.ReportSubmitValidationService;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStep;
import com.archdox.cloud.inspection.domain.PayloadStorageMode;
import com.archdox.cloud.inspection.dto.ReportSubmitValidationIssueResponse;
import com.archdox.cloud.inspection.dto.ReportSubmitValidationResponse;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.inspectiontarget.infra.InspectionReportTargetRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReportPreflightDeterministicValidatorTest {
    @Test
    void submitValidationBlockingIssueBlocksGeneration() {
        var submitValidationService = mock(ReportSubmitValidationService.class);
        var targetRepository = mock(InspectionReportTargetRepository.class);
        var stepRepository = mock(InspectionReportStepRepository.class);
        var report = report();
        when(submitValidationService.validate(report)).thenReturn(ReportSubmitValidationResponse.invalid(
                List.of(new ReportSubmitValidationIssueResponse(
                        "MISSING_STEP_BASIC_INFO",
                        "Basic info step must be saved before submit.",
                        "INSPECTION_REPORT_STEP",
                        "BASIC_INFO")),
                List.of()));
        when(targetRepository.findByOfficeIdAndReportIdOrderByRoleAscIdAsc(10L, 100L)).thenReturn(List.of());
        when(stepRepository.findByReportIdAndStepCode(100L, "DAILY_LOG")).thenReturn(Optional.empty());

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository, stepRepository)
                .validate(report);

        assertTrue(result.blocksGeneration());
        assertTrue(result.findings().stream().anyMatch(finding -> "MISSING_STEP_BASIC_INFO".equals(finding.code())));
    }

    @Test
    void constructionReportDoesNotWarnWhenTargetIsMissing() {
        var submitValidationService = mock(ReportSubmitValidationService.class);
        var targetRepository = mock(InspectionReportTargetRepository.class);
        var stepRepository = mock(InspectionReportStepRepository.class);
        var report = report("CONSTRUCTION_SUPERVISION_REPORT");
        when(submitValidationService.validate(report)).thenReturn(ReportSubmitValidationResponse.valid(List.of()));

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository, stepRepository)
                .validate(report);

        assertFalse(result.blocksGeneration());
        assertFalse(result.findings().stream().anyMatch(finding -> "REPORT_TARGET_NOT_SELECTED".equals(finding.code())));
        verify(targetRepository, never()).findByOfficeIdAndReportIdOrderByRoleAscIdAsc(10L, 100L);
    }

    @Test
    void dailySupervisionDoesNotWarnWhenTargetIsMissing() {
        var submitValidationService = mock(ReportSubmitValidationService.class);
        var targetRepository = mock(InspectionReportTargetRepository.class);
        var stepRepository = mock(InspectionReportStepRepository.class);
        var report = report("CONSTRUCTION_DAILY_SUPERVISION_LOG");
        when(submitValidationService.validate(report)).thenReturn(ReportSubmitValidationResponse.valid(List.of()));
        when(stepRepository.findByReportIdAndStepCode(100L, "DAILY_LOG")).thenReturn(Optional.empty());

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository, stepRepository)
                .validate(report);

        assertFalse(result.blocksGeneration());
        assertFalse(result.findings().stream().anyMatch(finding -> "REPORT_TARGET_NOT_SELECTED".equals(finding.code())));
        verify(targetRepository, never()).findByOfficeIdAndReportIdOrderByRoleAscIdAsc(10L, 100L);
    }

    @Test
    void dailySupervisionBlocksWhenStructuredItemsAreEmpty() {
        var submitValidationService = mock(ReportSubmitValidationService.class);
        var targetRepository = mock(InspectionReportTargetRepository.class);
        var stepRepository = mock(InspectionReportStepRepository.class);
        var report = report("CONSTRUCTION_DAILY_SUPERVISION_LOG");
        when(submitValidationService.validate(report)).thenReturn(ReportSubmitValidationResponse.valid(List.of()));
        when(stepRepository.findByReportIdAndStepCode(100L, "DAILY_LOG")).thenReturn(Optional.of(step(report, Map.of(
                "dailyItems", Map.of("groups", List.of())
        ))));

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository, stepRepository)
                .validate(report);

        assertTrue(result.blocksGeneration());
        assertTrue(result.findings().stream().anyMatch(finding -> "DAILY_LOG_GROUP_REQUIRED".equals(finding.code())));
    }

    @Test
    void dailySupervisionRequiresTradeProcessItemAndContent() {
        var submitValidationService = mock(ReportSubmitValidationService.class);
        var targetRepository = mock(InspectionReportTargetRepository.class);
        var stepRepository = mock(InspectionReportStepRepository.class);
        var report = report("CONSTRUCTION_DAILY_SUPERVISION_LOG");
        when(submitValidationService.validate(report)).thenReturn(ReportSubmitValidationResponse.valid(List.of()));
        when(stepRepository.findByReportIdAndStepCode(100L, "DAILY_LOG")).thenReturn(Optional.of(step(report, Map.of(
                "dailyItems", Map.of("groups", List.of(Map.of(
                        "floor", "",
                        "tradeName", "",
                        "processName", "",
                        "entries", List.of(Map.of(
                                "inspectionItemName", "",
                                "supervisionContent", "",
                                "photoIds", List.of()
                        ))
                )))
        ))));

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository, stepRepository)
                .validate(report);

        assertTrue(result.blocksGeneration());
        assertTrue(result.findings().stream().anyMatch(finding -> "DAILY_LOG_GROUP_FLOOR_REQUIRED".equals(finding.code())));
        assertTrue(result.findings().stream().anyMatch(finding -> "DAILY_LOG_GROUP_TRADE_REQUIRED".equals(finding.code())));
        assertTrue(result.findings().stream().anyMatch(finding -> "DAILY_LOG_GROUP_PROCESS_REQUIRED".equals(finding.code())));
        assertTrue(result.findings().stream().anyMatch(finding -> "DAILY_LOG_INSPECTION_ITEM_REQUIRED".equals(finding.code())));
        assertTrue(result.findings().stream().anyMatch(finding -> "DAILY_LOG_SUPERVISION_CONTENT_REQUIRED".equals(finding.code())));
        assertTrue(result.findings().stream().anyMatch(finding -> "DAILY_LOG_PHOTO_EVIDENCE_RECOMMENDED".equals(finding.code())));
    }

    @Test
    void dailySupervisionAcceptsJsonStringDailyItemsPayload() {
        var submitValidationService = mock(ReportSubmitValidationService.class);
        var targetRepository = mock(InspectionReportTargetRepository.class);
        var stepRepository = mock(InspectionReportStepRepository.class);
        var report = report("CONSTRUCTION_DAILY_SUPERVISION_LOG");
        when(submitValidationService.validate(report)).thenReturn(ReportSubmitValidationResponse.valid(List.of()));
        when(stepRepository.findByReportIdAndStepCode(100L, "DAILY_LOG")).thenReturn(Optional.of(step(report, Map.of(
                "dailyItems", """
                        {"groups":[{"floor":"전층","tradeName":"철근콘크리트공사","processName":"기초","entries":[{"inspectionItemName":"철근 배근 상태","supervisionContent":"철근 배근 상태를 확인했습니다.","photoIds":[1]}]}]}
                        """
        ))));

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository, stepRepository)
                .validate(report);

        assertFalse(result.blocksGeneration());
        assertFalse(result.findings().stream().anyMatch(finding -> finding.code().startsWith("DAILY_LOG_")));
    }

    private InspectionReport report() {
        return report("CONSTRUCTION_DAILY_SUPERVISION_LOG");
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

    private InspectionReportStep step(InspectionReport report, Map<String, Object> payload) {
        return new InspectionReportStep(
                report,
                "DAILY_LOG",
                PayloadStorageMode.CLOUD_ENCRYPTED,
                payload,
                20L,
                OffsetDateTime.now());
    }
}
