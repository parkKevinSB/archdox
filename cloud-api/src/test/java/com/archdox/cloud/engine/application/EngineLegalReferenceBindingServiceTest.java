package com.archdox.cloud.engine.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.archdox.cloud.legal.application.FakeLegalSourceClient;
import com.archdox.cloud.legal.infra.LegalActRepository;
import com.archdox.cloud.legal.infra.LegalArticleCorpusRow;
import com.archdox.cloud.legal.infra.LegalArticleRepository;
import com.archdox.cloud.legal.infra.LegalArticleVersionRepository;
import com.archdox.cloud.legal.infra.LegalDomainBindingRepository;
import com.archdox.cloud.legal.infra.LegalVersionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class EngineLegalReferenceBindingServiceTest {
    private final LegalDomainBindingRepository bindingRepository = mock(LegalDomainBindingRepository.class);
    private final LegalArticleVersionRepository articleVersionRepository = mock(LegalArticleVersionRepository.class);
    private final EngineLegalReferenceBindingService service = new EngineLegalReferenceBindingService(
            bindingRepository,
            mock(LegalActRepository.class),
            mock(LegalArticleRepository.class),
            mock(LegalVersionRepository.class),
            articleVersionRepository);

    @Test
    void resolvesConstructionSupervisionReferencesFromLegalCorpusWhenNoDomainBindingExists() {
        var effectiveDate = LocalDate.of(2026, 7, 1);
        when(bindingRepository.findByStatusAndCatalogCodeAndCatalogVersionAndChecklistItemCodeOrderByIdAsc(
                "ACTIVE",
                "CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24",
                2,
                "RC_REBAR_COUNT_DIAMETER_PITCH"))
                .thenReturn(List.of());
        when(bindingRepository.findByStatusAndReportTypeOrderByIdAsc("ACTIVE", "CONSTRUCTION_DAILY_SUPERVISION_LOG"))
                .thenReturn(List.of());
        when(articleVersionRepository.searchLatestArticles(
                eq("공사감리"),
                eq(true),
                eq("BUILDING_ACT"),
                eq(true),
                eq(""),
                eq(false),
                eq(""),
                eq(false),
                eq(effectiveDate),
                eq(FakeLegalSourceClient.DEFAULT_SOURCE_CODE),
                any(Pageable.class)))
                .thenReturn(List.of(corpusRow(497L, "25", "건축물의 공사감리")));
        when(articleVersionRepository.searchLatestArticles(
                eq("감리"),
                eq(true),
                eq("BUILDING_ACT"),
                eq(true),
                eq(""),
                eq(false),
                eq(""),
                eq(false),
                eq(effectiveDate),
                eq(FakeLegalSourceClient.DEFAULT_SOURCE_CODE),
                any(Pageable.class)))
                .thenReturn(List.of(corpusRow(498L, "25의2", "공사감리자의 업무")));

        var result = service.resolve(
                List.of(Map.of(
                        "catalogCode", "CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24",
                        "catalogVersion", 2,
                        "inspectionItemCode", "RC_REBAR_COUNT_DIAMETER_PITCH",
                        "inspectionItemName", "철근 개수·지름·피치",
                        "basis", "철근 개수, 지름, 피치 확인")),
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                effectiveDate);

        assertThat(result.legalReferences())
                .hasSize(2)
                .first()
                .satisfies(reference -> {
                    assertThat(reference).containsEntry("actCode", "BUILDING_ACT");
                    assertThat(reference).containsEntry("articleNo", "25");
                    assertThat(reference).containsEntry("bindingScope", "LEGAL_CORPUS_SEARCH");
                    assertThat(reference).containsEntry("relevance", "CANDIDATE");
                    assertThat(reference.get("referenceId").toString())
                            .contains("BUILDING_ACT", "25");
                    var metadata = (Map<?, ?>) reference.get("metadata");
                    assertThat(metadata.get("sourceCode")).isEqualTo("NATIONAL_LAW_OPEN_DATA");
                    assertThat(metadata.get("sourceUrl")).isEqualTo("https://www.law.go.kr/DRF/lawService.do?ID=001823");
                    assertThat(metadata.get("resolutionSource")).isEqualTo("LEGAL_CORPUS_SEARCH");
                });
        assertThat(result.metadata())
                .containsEntry("legalReferenceCorpusSearchApplied", true)
                .containsEntry("legalReferenceBindingCount", 0)
                .containsEntry("legalReferenceCorpusCount", 2)
                .containsEntry("source", "LEGAL_DOMAIN_BINDINGS_AND_CORPUS");
    }

    private LegalArticleCorpusRow corpusRow(Long articleVersionId, String articleNo, String articleTitle) {
        return new LegalArticleCorpusRow(
                100L,
                "BUILDING_ACT",
                "건축법",
                "LAW",
                "NATIONAL_LAW_OPEN_DATA",
                200L,
                "001823:20260701",
                LocalDate.of(2026, 7, 1),
                "https://www.law.go.kr/DRF/lawService.do?ID=001823",
                300L + articleVersionId,
                articleVersionId,
                articleNo.replace("의", "-"),
                articleNo,
                articleTitle,
                articleTitle + " 조문 본문",
                "hash-" + articleVersionId);
    }
}
