package com.archdox.cloud.engine.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EngineLegalRiskContextReviewServiceTest {
    private final EngineLegalRiskContextReviewService service = new EngineLegalRiskContextReviewService();

    @Test
    void aiPromptContextKeepsSourceBackedReferenceMetadata() {
        var result = service.review(
                Map.of("values", Map.of()),
                List.of(),
                List.of(Map.of(
                        "referenceId", "BUILDING_ACT:25@001823:20260701",
                        "actCode", "BUILDING_ACT",
                        "actName", "건축법",
                        "articleNo", "25",
                        "articleTitle", "건축물의 공사감리",
                        "legalVersionId", 200L,
                        "sourceVersionKey", "001823:20260701",
                        "effectiveDate", "2026-07-01",
                        "relevance", "CANDIDATE",
                        "metadata", Map.of(
                                "sourceCode", "NATIONAL_LAW_OPEN_DATA",
                                "sourceUrl", "https://www.law.go.kr/DRF/lawService.do?ID=001823",
                                "articleVersionId", 497L,
                                "resolutionSource", "LEGAL_CORPUS_SEARCH"))));

        var promptContext = (Map<?, ?>) result.metadata().get("aiPromptContext");
        var references = (List<?>) promptContext.get("legalReferences");
        assertThat(references).singleElement()
                .satisfies(reference -> {
                    var map = (Map<?, ?>) reference;
                    assertThat(map.get("sourceCode")).isEqualTo("NATIONAL_LAW_OPEN_DATA");
                    assertThat(map.get("sourceUrl")).isEqualTo("https://www.law.go.kr/DRF/lawService.do?ID=001823");
                    assertThat(map.get("articleVersionId")).isEqualTo("497");
                    assertThat(map.get("resolutionSource")).isEqualTo("LEGAL_CORPUS_SEARCH");
                    assertThat(map.get("effectiveDate")).isEqualTo("2026-07-01");
                });
        assertThat(result.findings())
                .extracting(ArchDoxEngineFinding::legalReferences)
                .contains(List.of("BUILDING_ACT:25@001823:20260701"));
        assertThat(result.findings()).singleElement()
                .satisfies(finding -> {
                    var summaries = (List<?>) finding.metadata().get("legalReferenceSummaries");
                    assertThat(summaries).singleElement()
                            .satisfies(summary -> {
                                var map = (Map<?, ?>) summary;
                                assertThat(map.get("actName")).isEqualTo("건축법");
                                assertThat(map.get("articleNo")).isEqualTo("25");
                                assertThat(map.get("sourceUrl")).isEqualTo("https://www.law.go.kr/DRF/lawService.do?ID=001823");
                            });
                    assertThat(String.valueOf(finding.metadata().get("evidenceRequirement")))
                            .contains("감리내용");
                });
    }

    @Test
    void materialPerformanceItemRequiresTechnicalEvidenceContextBeforePass() {
        var result = service.review(
                Map.of("values", Map.of(
                        "supervisionContent", Map.of(
                                "canonicalValue", "Window material performance checked; no abnormality noted.",
                                "rawValue", "Window material performance checked; no abnormality noted.",
                                "confidence", 0.92d),
                        "photoEvidence", Map.of(
                                "canonicalValue", "One product-label site photo was uploaded.",
                                "rawValue", "One product-label site photo was uploaded.",
                                "confidence", 0.88d))),
                List.of(Map.of(
                        "catalogCode", "CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24",
                        "catalogVersion", 2,
                        "tradeCode", "WINDOWS_DOORS",
                        "processCode", "GENERAL",
                        "inspectionItemCode", "WINDOW_MATERIAL",
                        "inspectionItemName", "Window material performance",
                        "basis", "KS and material-performance document check",
                        "location", "context.catalogSelection")),
                List.of(Map.of(
                        "referenceId", "CONSTRUCTION_SUPERVISION_DETAILED_STANDARD:000100@test",
                        "actCode", "CONSTRUCTION_SUPERVISION_DETAILED_STANDARD",
                        "actName", "Construction supervision detailed standard",
                        "articleNo", "appendix 1",
                        "articleTitle", "Checklist",
                        "legalVersionId", 1L,
                        "sourceVersionKey", "test",
                        "effectiveDate", "2026-01-01",
                        "relevance", "REFERENCE",
                        "metadata", Map.of(
                                "resolutionSource", "LEGAL_DOMAIN_BINDING"))));

        assertThat(result.findings())
                .extracting(ArchDoxEngineFinding::code)
                .contains("LEGAL_TECHNICAL_EVIDENCE_CONTEXT_LIMITED");
        assertThat(result.metadata())
                .containsEntry("technicalEvidenceRequired", true)
                .containsEntry("technicalEvidenceContextPresent", false);
    }

    @Test
    void materialPerformanceItemAllowsExplicitTechnicalEvidenceContext() {
        var result = service.review(
                Map.of("values", Map.of(
                        "supervisionContent", Map.of(
                                "canonicalValue", "Window material performance was checked against specifications and test reports.",
                                "rawValue", "Window material performance was checked against specifications and test reports.",
                                "confidence", 0.92d),
                        "evidenceText", Map.of(
                                "canonicalValue", "Specifications, material approval, and test report were separately reviewed.",
                                "rawValue", "Specifications, material approval, and test report were separately reviewed.",
                                "confidence", 0.90d))),
                List.of(Map.of(
                        "catalogCode", "CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24",
                        "catalogVersion", 2,
                        "tradeCode", "WINDOWS_DOORS",
                        "processCode", "GENERAL",
                        "inspectionItemCode", "WINDOW_MATERIAL",
                        "inspectionItemName", "Window material performance",
                        "basis", "KS and material-performance document check")),
                List.of(Map.of(
                        "referenceId", "CONSTRUCTION_SUPERVISION_DETAILED_STANDARD:000100@test",
                        "actCode", "CONSTRUCTION_SUPERVISION_DETAILED_STANDARD",
                        "actName", "Construction supervision detailed standard",
                        "articleNo", "appendix 1",
                        "articleTitle", "Checklist",
                        "legalVersionId", 1L,
                        "sourceVersionKey", "test",
                        "effectiveDate", "2026-01-01",
                        "relevance", "REFERENCE",
                        "metadata", Map.of(
                                "resolutionSource", "LEGAL_DOMAIN_BINDING"))));

        assertThat(result.findings())
                .extracting(ArchDoxEngineFinding::code)
                .doesNotContain("LEGAL_TECHNICAL_EVIDENCE_CONTEXT_LIMITED");
        assertThat(result.metadata())
                .containsEntry("technicalEvidenceRequired", true)
                .containsEntry("technicalEvidenceContextPresent", true);
    }
}
