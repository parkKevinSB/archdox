package com.archdox.cloud.engine.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.archdox.cloud.legal.application.FakeLegalSourceClient;
import com.archdox.cloud.legal.domain.LegalAct;
import com.archdox.cloud.legal.domain.LegalArticle;
import com.archdox.cloud.legal.domain.LegalArticleVersion;
import com.archdox.cloud.legal.domain.LegalDomainBinding;
import com.archdox.cloud.legal.domain.LegalVersion;
import com.archdox.cloud.legal.infra.LegalActRepository;
import com.archdox.cloud.legal.infra.LegalArticleCorpusRow;
import com.archdox.cloud.legal.infra.LegalArticleRepository;
import com.archdox.cloud.legal.infra.LegalArticleVersionRepository;
import com.archdox.cloud.legal.infra.LegalDomainBindingRepository;
import com.archdox.cloud.legal.infra.LegalVersionRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class EngineLegalReferenceBindingServiceTest {
    private final LegalDomainBindingRepository bindingRepository = mock(LegalDomainBindingRepository.class);
    private final LegalActRepository actRepository = mock(LegalActRepository.class);
    private final LegalArticleRepository articleRepository = mock(LegalArticleRepository.class);
    private final LegalVersionRepository versionRepository = mock(LegalVersionRepository.class);
    private final LegalArticleVersionRepository articleVersionRepository = mock(LegalArticleVersionRepository.class);
    private final EngineLegalReferenceBindingService service = new EngineLegalReferenceBindingService(
            bindingRepository,
            actRepository,
            articleRepository,
            versionRepository,
            articleVersionRepository);

    @Test
    void resolvesActiveDomainBindingBeforeCorpusFallback() {
        var now = OffsetDateTime.parse("2026-06-09T10:00:00+09:00");
        var effectiveDate = LocalDate.of(2026, 7, 1);
        var binding = new LegalDomainBinding(
                "CATALOG_ITEM",
                "CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24:v2:RC_REBAR_COUNT_DIAMETER_PITCH",
                100L,
                200L,
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24",
                2,
                "RC_REBAR_COUNT_DIAMETER_PITCH",
                "PRIMARY",
                "ACTIVE",
                null,
                null,
                "Primary basis for rebar count, diameter, and pitch checks.",
                Map.of("source", "admin"),
                now);
        ReflectionTestUtils.setField(binding, "id", 400L);
        var act = act(100L, now);
        var article = article(200L, 100L, now);
        var version = version(300L, 100L, effectiveDate, now);
        var articleVersion = articleVersion(500L, 200L, 300L, effectiveDate, now);

        when(bindingRepository.findByStatusAndCatalogCodeAndCatalogVersionAndChecklistItemCodeOrderByIdAsc(
                "ACTIVE",
                "CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24",
                2,
                "RC_REBAR_COUNT_DIAMETER_PITCH"))
                .thenReturn(List.of(binding));
        when(bindingRepository.findByStatusAndReportTypeOrderByIdAsc("ACTIVE", "CONSTRUCTION_DAILY_SUPERVISION_LOG"))
                .thenReturn(List.of());
        when(actRepository.findById(100L)).thenReturn(Optional.of(act));
        when(articleRepository.findById(200L)).thenReturn(Optional.of(article));
        when(versionRepository.findFirstByActIdOrderByCapturedAtDescIdDesc(100L)).thenReturn(Optional.of(version));
        when(articleVersionRepository.findByArticleIdAndLegalVersionId(200L, 300L)).thenReturn(Optional.of(articleVersion));

        var result = service.resolve(
                List.of(Map.of(
                        "catalogCode", "CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24",
                        "catalogVersion", 2,
                        "inspectionItemCode", "RC_REBAR_COUNT_DIAMETER_PITCH",
                        "inspectionItemName", "철근 개수·지름·피치")),
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                effectiveDate);

        assertThat(result.legalReferences())
                .hasSize(1)
                .first()
                .satisfies(reference -> {
                    assertThat(reference).containsEntry("actCode", "BUILDING_ACT");
                    assertThat(reference).containsEntry("articleNo", "제25조");
                    assertThat(reference).containsEntry("articleTitle", "건축물의 공사감리");
                    assertThat(reference).containsEntry("bindingScope", "CATALOG_ITEM");
                    assertThat(reference).containsEntry("bindingKey", "CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24:v2:RC_REBAR_COUNT_DIAMETER_PITCH");
                    assertThat(reference).containsEntry("relevance", "PRIMARY");
                    assertThat(reference.get("referenceId").toString())
                            .isEqualTo("BUILDING_ACT:0025001@001823:20260701");
                    var metadata = (Map<?, ?>) reference.get("metadata");
                    assertThat(metadata.get("resolutionSource")).isEqualTo("LEGAL_DOMAIN_BINDING");
                    assertThat(metadata.get("bindingId")).isEqualTo(400L);
                });
        assertThat(result.metadata())
                .containsEntry("legalReferenceBindingApplied", true)
                .containsEntry("legalReferenceBindingCount", 1)
                .containsEntry("legalReferenceCorpusCount", 0);
    }

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

    private LegalAct act(Long id, OffsetDateTime now) {
        var act = new LegalAct(1L, "BUILDING_ACT", "건축법", "LAW", "KR", "001823", now);
        ReflectionTestUtils.setField(act, "id", id);
        return act;
    }

    private LegalArticle article(Long id, Long actId, OffsetDateTime now) {
        var article = new LegalArticle(actId, "0025001", "25", "건축물의 공사감리", null, 25, now);
        ReflectionTestUtils.setField(article, "id", id);
        return article;
    }

    private LegalVersion version(Long id, Long actId, LocalDate effectiveDate, OffsetDateTime now) {
        var version = new LegalVersion(
                actId,
                "001823:20260701",
                LocalDate.of(2026, 6, 1),
                effectiveDate,
                "https://www.law.go.kr/법령/건축법",
                "version-hash",
                Map.of(),
                now);
        ReflectionTestUtils.setField(version, "id", id);
        return version;
    }

    private LegalArticleVersion articleVersion(
            Long id,
            Long articleId,
            Long versionId,
            LocalDate effectiveDate,
            OffsetDateTime now
    ) {
        var articleVersion = new LegalArticleVersion(
                articleId,
                versionId,
                "0025001",
                "제25조",
                "건축물의 공사감리",
                "제25조 건축물의 공사감리 조문 본문",
                "제25조 건축물의 공사감리 조문 본문",
                "article-hash",
                effectiveDate,
                Map.of(),
                now);
        ReflectionTestUtils.setField(articleVersion, "id", id);
        return articleVersion;
    }
}
