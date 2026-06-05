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
    }
}
