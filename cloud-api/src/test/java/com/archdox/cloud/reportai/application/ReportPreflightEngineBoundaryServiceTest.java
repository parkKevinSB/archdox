package com.archdox.cloud.reportai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.archdox.cloud.engine.application.ArchDoxEngineFinding;
import com.archdox.cloud.engine.application.ArchDoxEngineResultStatus;
import com.archdox.cloud.engine.application.EngineCatalogBindingReviewService;
import com.archdox.cloud.engine.application.EngineLegalReferenceBindingService;
import com.archdox.cloud.engine.application.EngineLegalRiskContextReviewService;
import com.archdox.cloud.engine.application.EngineValidationService;
import com.archdox.cloud.inspection.domain.InspectionReport;
import com.archdox.cloud.inspection.domain.InspectionReportStep;
import com.archdox.cloud.inspection.domain.PayloadStorageMode;
import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import com.archdox.cloud.legal.infra.LegalActRepository;
import com.archdox.cloud.legal.infra.LegalArticleRepository;
import com.archdox.cloud.legal.infra.LegalArticleVersionRepository;
import com.archdox.cloud.legal.infra.LegalDomainBindingRepository;
import com.archdox.cloud.legal.infra.LegalVersionRepository;
import com.archdox.cloud.photo.domain.PhotoStatus;
import com.archdox.cloud.photo.infra.PhotoRepository;
import com.archdox.cloud.supervisioncatalog.application.SupervisionDomainCatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReportPreflightEngineBoundaryServiceTest {
    private final InspectionReportStepRepository stepRepository = mock(InspectionReportStepRepository.class);
    private final PhotoRepository photoRepository = mock(PhotoRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReportPreflightEngineBoundaryService service = new ReportPreflightEngineBoundaryService(
            stepRepository,
            photoRepository,
            validationService(),
            objectMapper);

    @Test
    void validatesDailyLogCatalogSelectionsThroughEngineBoundary() {
        var report = report();
        when(stepRepository.findByReportIdAndStepCode(100L, "DAILY_LOG"))
                .thenReturn(Optional.of(step(report, "DAILY_LOG", dailyLogPayload("RC_REBAR_CONFIRMATION"))));
        when(stepRepository.findByReportIdAndStepCode(100L, "REMARKS"))
                .thenReturn(Optional.of(step(report, "REMARKS", Map.of(
                        "specialNotes", "특기사항 없음.",
                        "issueAndAction", "지적사항 및 처리결과 없음."))));
        when(photoRepository.findByOfficeIdAndReportIdAndStatusNotOrderByIdDesc(10L, 100L, PhotoStatus.DELETED))
                .thenReturn(List.of());

        var result = service.validate(report, 50L);

        assertThat(result.status()).isEqualTo(ArchDoxEngineResultStatus.PASS);
        assertThat(result.findings()).isEmpty();
        assertThat(result.metadata().get("catalogBindings").toString())
                .contains("REINFORCED_CONCRETE", "REBAR_ASSEMBLY", "RC_REBAR_CONFIRMATION");
        assertThat(result.metadata().get("governanceBoundary")).isEqualTo("ARCHDOX_WORKER_SERVICE");
        assertThat(result.executedActions())
                .containsExactly(
                        "CATALOG_BINDING_REVIEW",
                        "DOCUMENT_QUALITY_REVIEW",
                        "LEGAL_REFERENCE_BINDING",
                        "LEGAL_RISK_CONTEXT_REVIEW",
                        "RETURN_TYPED_RESULT");
    }

    @Test
    void returnsEngineFindingWhenDailyLogCatalogSelectionIsInvalid() {
        var report = report();
        when(stepRepository.findByReportIdAndStepCode(100L, "DAILY_LOG"))
                .thenReturn(Optional.of(step(report, "DAILY_LOG", dailyLogPayload("NOT_A_REAL_ITEM"))));
        when(stepRepository.findByReportIdAndStepCode(100L, "REMARKS"))
                .thenReturn(Optional.of(step(report, "REMARKS", Map.of(
                        "specialNotes", "특기사항 없음.",
                        "issueAndAction", "지적사항 및 처리결과 없음."))));
        when(photoRepository.findByOfficeIdAndReportIdAndStatusNotOrderByIdDesc(10L, 100L, PhotoStatus.DELETED))
                .thenReturn(List.of());

        var result = service.validate(report, 50L);

        assertThat(result.status()).isEqualTo(ArchDoxEngineResultStatus.FAIL);
        assertThat(result.generationAllowed()).isFalse();
        assertThat(result.findings())
                .extracting(ArchDoxEngineFinding::code)
                .contains("CATALOG_SELECTION_INVALID");
    }

    private EngineValidationService validationService() {
        var legalBindingRepository = mock(LegalDomainBindingRepository.class);
        var legalActRepository = mock(LegalActRepository.class);
        var legalArticleRepository = mock(LegalArticleRepository.class);
        var legalVersionRepository = mock(LegalVersionRepository.class);
        var legalArticleVersionRepository = mock(LegalArticleVersionRepository.class);
        return new EngineValidationService(
                new EngineCatalogBindingReviewService(new SupervisionDomainCatalogService(objectMapper)),
                new EngineLegalReferenceBindingService(
                        legalBindingRepository,
                        legalActRepository,
                        legalArticleRepository,
                        legalVersionRepository,
                        legalArticleVersionRepository),
                new EngineLegalRiskContextReviewService());
    }

    private InspectionReport report() {
        var report = new InspectionReport(
                10L,
                20L,
                30L,
                "R-001",
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "construction daily supervision log",
                40L,
                50L,
                OffsetDateTime.now());
        ReflectionTestUtils.setField(report, "id", 100L);
        return report;
    }

    private InspectionReportStep step(InspectionReport report, String stepCode, Map<String, Object> payload) {
        return new InspectionReportStep(
                report,
                stepCode,
                PayloadStorageMode.CLOUD_ENCRYPTED,
                payload,
                50L,
                OffsetDateTime.now());
    }

    private Map<String, Object> dailyLogPayload(String inspectionItemCode) {
        var entry = Map.of(
                "inspectionItemCode", inspectionItemCode,
                "inspectionItemName", "철근배근의 확인사항",
                "checklistRows", List.of(Map.of(
                        "code", "RC_REBAR_COUNT_DIAMETER_PITCH",
                        "label", "Checked rebar count, diameter, and pitch.",
                        "result", "COMPLIANT",
                        "referenceNote", "Checked rebar count, diameter, and pitch.",
                        "photoIds", List.of(1L))));
        return Map.of(
                "dailyItems", Map.of(
                        "groups", List.of(Map.of(
                                "floor", "1F",
                                "tradeCode", "REINFORCED_CONCRETE",
                                "processCode", "REBAR_ASSEMBLY",
                                "entries", List.of(entry)))));
    }
}
