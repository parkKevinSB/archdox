package com.archdox.cloud.legal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.legal.infra.LegalArticleCorpusRow;
import com.archdox.cloud.legal.infra.LegalArticleVersionRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class LegalCorpusReadServiceTest {
    private final LegalArticleVersionRepository repository = mock(LegalArticleVersionRepository.class);
    private final LegalCorpusReadService service = new LegalCorpusReadService(repository);

    @Test
    void searchReturnsSourceBackedArticleSnippets() {
        var effectiveDate = LocalDate.of(2026, 7, 1);
        when(repository.searchLatestArticles(
                eq("감리"),
                eq(true),
                eq("BUILDING_ACT"),
                eq(true),
                eq("건축법"),
                eq(true),
                eq("25의2"),
                eq(true),
                eq(effectiveDate),
                eq(FakeLegalSourceClient.DEFAULT_SOURCE_CODE),
                any(Pageable.class)))
                .thenReturn(List.of(row()));

        var response = service.search("감리", "building_act", "건축법", "제25조의2", effectiveDate, 200);

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.limit()).isEqualTo(50);
        assertThat(response.articleNo()).isEqualTo("25의2");
        assertThat(response.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.sourceCode()).isEqualTo("NATIONAL_LAW_OPEN_DATA");
                    assertThat(item.actCode()).isEqualTo("BUILDING_ACT");
                    assertThat(item.articleVersionId()).isEqualTo(400L);
                    assertThat(item.snippet()).contains("감리");
                    assertThat(item.snippet()).doesNotContain("second paragraph");
                });
        verify(repository).searchLatestArticles(
                eq("감리"),
                eq(true),
                eq("BUILDING_ACT"),
                eq(true),
                eq("건축법"),
                eq(true),
                eq("25의2"),
                eq(true),
                eq(effectiveDate),
                eq(FakeLegalSourceClient.DEFAULT_SOURCE_CODE),
                any(Pageable.class));
    }

    @Test
    void searchRequiresAtLeastOneSelector() {
        assertThatThrownBy(() -> service.search(null, null, null, null, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("query, actCode, actName, or articleNo is required");
    }

    @Test
    void getArticleReadsLatestArticleByActCodeAndArticleNumber() {
        when(repository.findLatestCorpusRowsByActCodeAndArticleNo(
                eq("BUILDING_ACT"),
                eq("25의2"),
                eq(null),
                eq(FakeLegalSourceClient.DEFAULT_SOURCE_CODE),
                any(Pageable.class)))
                .thenReturn(List.of(row()));

        var response = service.getArticle(null, null, "building_act", "제25조의2", null);

        assertThat(response.articleText()).contains("감리자는");
        assertThat(response.articleText()).contains("second paragraph");
        assertThat(response.sourceUrl()).isEqualTo("https://www.law.go.kr/DRF/lawService.do?ID=001823");
    }

    private LegalArticleCorpusRow row() {
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
                300L,
                400L,
                "25-2",
                "25의2",
                "공사감리",
                "감리자는 공사감리 업무를 수행한다. " + "내용 ".repeat(100) + "second paragraph",
                "hash-1");
    }
}
