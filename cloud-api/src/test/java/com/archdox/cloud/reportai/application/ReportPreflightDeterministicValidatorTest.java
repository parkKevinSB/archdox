package com.archdox.cloud.reportai.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReportPreflightDeterministicValidatorTest {
    private final ReportPhotoEvidenceStatusService photoEvidenceStatusService = mock(ReportPhotoEvidenceStatusService.class);

    @BeforeEach
    void setUp() {
        when(photoEvidenceStatusService.evaluate(any())).thenReturn(emptyPhotoEvidenceStatus());
    }

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

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository, stepRepository, photoEvidenceStatusService)
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

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository, stepRepository, photoEvidenceStatusService)
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

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository, stepRepository, photoEvidenceStatusService)
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

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository, stepRepository, photoEvidenceStatusService)
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
                                "photoIds", List.of()
                        ))
                )))
        ))));

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository, stepRepository, photoEvidenceStatusService)
                .validate(report);

        assertTrue(result.blocksGeneration());
        assertTrue(result.findings().stream().anyMatch(finding -> "DAILY_LOG_GROUP_FLOOR_REQUIRED".equals(finding.code())));
        assertTrue(result.findings().stream().anyMatch(finding -> "DAILY_LOG_GROUP_TRADE_REQUIRED".equals(finding.code())));
        assertTrue(result.findings().stream().anyMatch(finding -> "DAILY_LOG_GROUP_PROCESS_REQUIRED".equals(finding.code())));
        assertTrue(result.findings().stream().anyMatch(finding -> "DAILY_LOG_INSPECTION_ITEM_REQUIRED".equals(finding.code())));
        assertTrue(result.findings().stream().anyMatch(finding -> "DAILY_LOG_CHECKLIST_ROWS_REQUIRED".equals(finding.code())));
        assertTrue(result.findings().stream().anyMatch(finding -> "DAILY_LOG_INSPECTED_CHECKLIST_ROW_REQUIRED".equals(finding.code())));
    }

    @Test
    void dailySupervisionBlocksNameOnlyLegacyCatalogData() {
        var submitValidationService = mock(ReportSubmitValidationService.class);
        var targetRepository = mock(InspectionReportTargetRepository.class);
        var stepRepository = mock(InspectionReportStepRepository.class);
        var report = report("CONSTRUCTION_DAILY_SUPERVISION_LOG");
        when(submitValidationService.validate(report)).thenReturn(ReportSubmitValidationResponse.valid(List.of()));
        when(stepRepository.findByReportIdAndStepCode(100L, "DAILY_LOG")).thenReturn(Optional.of(step(report, Map.of(
                "dailyItems", Map.of("groups", List.of(Map.of(
                        "floor", "전층",
                        "tradeName", "철근콘크리트공사",
                        "processName", "기초",
                        "entries", List.of(Map.of(
                                "inspectionItemName", "철근 배근 상태",
                                "photoIds", List.of(1)
                        ))
                )))
        ))));

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository, stepRepository, photoEvidenceStatusService)
                .validate(report);

        assertTrue(result.blocksGeneration());
        assertTrue(result.findings().stream().anyMatch(finding -> "DAILY_LOG_GROUP_TRADE_CODE_REQUIRED".equals(finding.code())));
        assertTrue(result.findings().stream().anyMatch(finding -> "DAILY_LOG_GROUP_PROCESS_CODE_REQUIRED".equals(finding.code())));
        assertTrue(result.findings().stream().anyMatch(finding -> "DAILY_LOG_INSPECTION_ITEM_CODE_REQUIRED".equals(finding.code())));
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
                        {"groups":[{"floor":"전층","tradeCode":"REINFORCED_CONCRETE","tradeName":"철근콘크리트공사","processCode":"REBAR_ASSEMBLY","processName":"철근 조립","entries":[{"inspectionItemCode":"RC_REBAR_CONFIRMATION","inspectionItemName":"철근배근의 확인사항","checklistRows":[{"code":"RC_REBAR_COUNT_DIAMETER_PITCH","label":"개수, 철근지름, 피치 확인","result":"COMPLIANT","referenceNote":"철근 배근 상태를 확인했습니다.","photoIds":[1]}]}]}]}
                        """
        ))));

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository, stepRepository, photoEvidenceStatusService)
                .validate(report);

        assertFalse(result.blocksGeneration());
        assertFalse(result.findings().stream().anyMatch(finding -> finding.code().startsWith("DAILY_LOG_")));
    }

    @Test
    void dailySupervisionFlagsDeterministicWordingLint() {
        var submitValidationService = mock(ReportSubmitValidationService.class);
        var targetRepository = mock(InspectionReportTargetRepository.class);
        var stepRepository = mock(InspectionReportStepRepository.class);
        var report = report("CONSTRUCTION_DAILY_SUPERVISION_LOG");
        var content = "column \uACFC \uB97C \uD655\uC774\uD588\uC501\uB2C8\uB2E4.";
        when(submitValidationService.validate(report)).thenReturn(ReportSubmitValidationResponse.valid(List.of()));
        when(stepRepository.findByReportIdAndStepCode(100L, "DAILY_LOG")).thenReturn(Optional.of(step(report, Map.of(
                "dailyItems", Map.of("groups", List.of(Map.of(
                        "floor", "1F",
                        "tradeCode", "STEEL",
                        "processCode", "COLUMN",
                        "entries", List.of(Map.of(
                                "inspectionItemCode", "STEEL_MEMBER_SYMBOL",
                                "checklistRows", List.of(Map.of(
                                        "code", "STEEL_MEMBER_SYMBOL_DETAIL",
                                        "label", "기둥·보 부호 확인",
                                        "result", "COMPLIANT",
                                        "referenceNote", content,
                                        "photoIds", List.of(101L)))
                        ))
                )))
        ))));

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository, stepRepository, photoEvidenceStatusService)
                .validate(report);

        assertTrue(result.blocksGeneration());
        assertTrue(result.findings().stream()
                .anyMatch(finding -> "DAILY_LOG_WORDING_TYPO".equals(finding.code())
                        && "true".equals(finding.attributes().get("approvalRequired"))
                        && "WORDING".equals(finding.attributes().get("category"))
                        && finding.attributes().get("replacement").contains("\uD655\uC778\uD588\uC2B5\uB2C8\uB2E4")));
        assertTrue(result.findings().stream()
                .anyMatch(finding -> "DAILY_LOG_WORDING_PARTICLE_SEQUENCE".equals(finding.code())
                        && "true".equals(finding.attributes().get("approvalRequired"))));
    }

    @Test
    void dailySupervisionBlocksWhenLinkedPhotoReferenceIsBroken() {
        var submitValidationService = mock(ReportSubmitValidationService.class);
        var targetRepository = mock(InspectionReportTargetRepository.class);
        var stepRepository = mock(InspectionReportStepRepository.class);
        var report = report("CONSTRUCTION_DAILY_SUPERVISION_LOG");
        when(submitValidationService.validate(report)).thenReturn(ReportSubmitValidationResponse.valid(List.of()));
        when(stepRepository.findByReportIdAndStepCode(100L, "DAILY_LOG")).thenReturn(Optional.of(step(report, Map.of(
                "dailyItems", Map.of("groups", List.of(Map.of(
                        "floor", "전층",
                        "tradeCode", "REINFORCED_CONCRETE",
                        "processCode", "REBAR_ASSEMBLY",
                        "entries", List.of(Map.of(
                                "inspectionItemCode", "RC_REBAR_CONFIRMATION",
                                "checklistRows", List.of(Map.of(
                                        "code", "RC_REBAR_COUNT_DIAMETER_PITCH",
                                        "label", "개수, 철근지름, 피치 확인",
                                        "result", "COMPLIANT",
                                        "referenceNote", "철근 배근 상태를 확인했습니다.",
                                        "photoIds", List.of(101L)))
                        ))
                )))
        ))));
        when(photoEvidenceStatusService.evaluate(report)).thenReturn(new ReportPhotoEvidenceStatus(
                true,
                0,
                0,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(101L),
                Set.of(),
                Set.of(101L),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of()));

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository, stepRepository, photoEvidenceStatusService)
                .validate(report);

        assertTrue(result.blocksGeneration());
        assertTrue(result.findings().stream().anyMatch(finding -> "DAILY_LOG_PHOTO_REFERENCE_MISSING".equals(finding.code())));
    }

    @Test
    void dailySupervisionWarnsWhenReportPhotoIsNotLinkedToDailyLog() {
        var submitValidationService = mock(ReportSubmitValidationService.class);
        var targetRepository = mock(InspectionReportTargetRepository.class);
        var stepRepository = mock(InspectionReportStepRepository.class);
        var report = report("CONSTRUCTION_DAILY_SUPERVISION_LOG");
        when(submitValidationService.validate(report)).thenReturn(ReportSubmitValidationResponse.valid(List.of()));
        when(stepRepository.findByReportIdAndStepCode(100L, "DAILY_LOG")).thenReturn(Optional.of(step(report, Map.of(
                "dailyItems", Map.of("groups", List.of(Map.of(
                        "floor", "전층",
                        "tradeCode", "REINFORCED_CONCRETE",
                        "processCode", "REBAR_ASSEMBLY",
                        "entries", List.of(Map.of(
                                "inspectionItemCode", "RC_REBAR_CONFIRMATION",
                                "checklistRows", List.of(Map.of(
                                        "code", "RC_REBAR_COUNT_DIAMETER_PITCH",
                                        "label", "개수, 철근지름, 피치 확인",
                                        "result", "COMPLIANT",
                                        "referenceNote", "철근 배근 상태를 확인했습니다.",
                                        "photoIds", List.of(101L)))
                        ))
                )))
        ))));
        when(photoEvidenceStatusService.evaluate(report)).thenReturn(new ReportPhotoEvidenceStatus(
                true,
                2,
                2,
                Set.of(101L, 202L),
                Set.of(101L, 202L),
                Set.of(101L, 202L),
                Set.of(101L),
                Set.of(101L),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(202L)));

        var result = new ReportPreflightDeterministicValidator(submitValidationService, targetRepository, stepRepository, photoEvidenceStatusService)
                .validate(report);

        assertFalse(result.blocksGeneration());
        assertTrue(result.findings().stream().anyMatch(finding -> "REPORT_PHOTO_UNLINKED_TO_DAILY_LOG".equals(finding.code())));
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

    private ReportPhotoEvidenceStatus emptyPhotoEvidenceStatus() {
        return new ReportPhotoEvidenceStatus(
                true,
                0,
                0,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of());
    }
}
