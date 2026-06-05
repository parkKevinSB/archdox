package com.archdox.cloud.legal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.legal.domain.LegalChangeDigest;
import com.archdox.cloud.legal.domain.LegalChangeDigestSource;
import com.archdox.cloud.legal.domain.LegalChangeDigestStatus;
import com.archdox.cloud.legal.domain.LegalArticleChangeType;
import com.archdox.cloud.legal.domain.LegalArticleDiff;
import com.archdox.cloud.legal.domain.LegalArticleVersion;
import com.archdox.cloud.legal.domain.LegalVersion;
import com.archdox.cloud.legal.infra.LegalArticleDiffRepository;
import com.archdox.cloud.legal.infra.LegalArticleVersionRepository;
import com.archdox.cloud.legal.infra.LegalChangeDigestRepository;
import com.archdox.cloud.legal.infra.LegalVersionRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class LegalUpdateReadServiceTest {
    private final LegalChangeDigestRepository repository = mock(LegalChangeDigestRepository.class);
    private final LegalArticleDiffRepository articleDiffRepository = mock(LegalArticleDiffRepository.class);
    private final LegalArticleVersionRepository articleVersionRepository = mock(LegalArticleVersionRepository.class);
    private final LegalVersionRepository versionRepository = mock(LegalVersionRepository.class);
    private final LegalUpdateReadService service = new LegalUpdateReadService(
            repository,
            articleDiffRepository,
            articleVersionRepository,
            versionRepository);

    @Test
    void recentExcludesFakeLegalSource() throws Exception {
        var digest = digest(10L, 100L);
        when(repository.findPublishedExcludingSourceCode(
                eq(LegalChangeDigestStatus.PUBLISHED),
                any(),
                eq(FakeLegalSourceClient.DEFAULT_SOURCE_CODE),
                any(Pageable.class)))
                .thenReturn(List.of(digest));
        when(articleDiffRepository.findByChangeSetIdOrderByIdAsc(100L))
                .thenReturn(List.of(diff(1L, 100L)));
        when(articleVersionRepository.findAllById(any()))
                .thenReturn(List.of(articleVersion(250L, 300L)));
        when(versionRepository.findAllById(any()))
                .thenReturn(List.of(legalVersion(300L)));

        var updates = service.recent(30, 50);

        assertThat(updates).singleElement()
                .satisfies(update -> {
                    assertThat(update.id()).isEqualTo(10L);
                    assertThat(update.title()).isEqualTo("건축법 조문 변경: 신설 1건");
                    assertThat(update.articleDiffs()).singleElement()
                            .satisfies(diff -> {
                                assertThat(diff.articleNo()).isEqualTo("제25조");
                                assertThat(diff.articleTitle()).isEqualTo("공사감리");
                                assertThat(diff.changeType()).isEqualTo(LegalArticleChangeType.ADDED);
                                assertThat(diff.afterTextPreview()).contains("감리자는 공사감리 업무를 수행한다");
                                assertThat(diff.sourceUrl()).isEqualTo("https://www.law.go.kr/DRF/lawService.do?target=law&type=JSON&ID=001823");
                            });
                });
        verify(repository).findPublishedExcludingSourceCode(
                eq(LegalChangeDigestStatus.PUBLISHED),
                any(),
                eq(FakeLegalSourceClient.DEFAULT_SOURCE_CODE),
                any(Pageable.class));
    }

    @Test
    void detailExcludesFakeLegalSource() throws Exception {
        when(repository.findPublishedByIdExcludingSourceCode(
                10L,
                LegalChangeDigestStatus.PUBLISHED,
                FakeLegalSourceClient.DEFAULT_SOURCE_CODE))
                .thenReturn(Optional.of(digest(10L, 100L)));
        when(articleDiffRepository.findByChangeSetIdOrderByIdAsc(100L))
                .thenReturn(List.of(diff(1L, 100L)));
        when(articleVersionRepository.findAllById(any()))
                .thenReturn(List.of(articleVersion(250L, 300L)));
        when(versionRepository.findAllById(any()))
                .thenReturn(List.of(legalVersion(300L)));

        var update = service.detail(10L);

        assertThat(update.id()).isEqualTo(10L);
        assertThat(update.changeSetId()).isEqualTo(100L);
        assertThat(update.articleDiffs()).hasSize(1);
    }

    private LegalChangeDigest digest(Long id, Long changeSetId) throws Exception {
        var now = OffsetDateTime.parse("2026-06-05T09:00:00+09:00");
        var digest = new LegalChangeDigest(
                changeSetId,
                LegalChangeDigestStatus.PUBLISHED,
                LegalChangeDigestSource.DETERMINISTIC,
                "건축법 조문 변경: 신설 1건",
                "summary",
                "impact",
                List.of("CONSTRUCTION_DAILY_SUPERVISION_LOG"),
                List.of("CONSTRUCTION_SUPERVISION_CHECKLIST"),
                null,
                LocalDate.of(2026, 7, 1),
                now,
                now,
                now);
        setId(digest, id);
        return digest;
    }

    private LegalArticleDiff diff(Long id, Long changeSetId) throws Exception {
        var now = OffsetDateTime.parse("2026-06-05T09:00:00+09:00");
        var diff = new LegalArticleDiff(
                changeSetId,
                25L,
                "0025001",
                "제25조",
                LegalArticleChangeType.ADDED,
                null,
                250L,
                null,
                "after-hash",
                "Article added: 제25조 공사감리",
                now);
        setId(diff, id);
        return diff;
    }

    private LegalArticleVersion articleVersion(Long id, Long legalVersionId) throws Exception {
        var now = OffsetDateTime.parse("2026-06-05T09:00:00+09:00");
        var articleVersion = new LegalArticleVersion(
                25L,
                legalVersionId,
                "0025001",
                "제25조",
                "공사감리",
                "감리자는 공사감리 업무를 수행한다. 현장 증빙과 감리 내용을 기록한다.",
                "감리자는 공사감리 업무를 수행한다 현장 증빙과 감리 내용을 기록한다",
                "after-hash",
                LocalDate.of(2026, 7, 1),
                Map.of(),
                now);
        setId(articleVersion, id);
        return articleVersion;
    }

    private LegalVersion legalVersion(Long id) throws Exception {
        var now = OffsetDateTime.parse("2026-06-05T09:00:00+09:00");
        var version = new LegalVersion(
                1L,
                "0018232026070100000",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 1),
                "https://www.law.go.kr/DRF/lawService.do?target=law&type=JSON&ID=001823",
                "version-hash",
                Map.of(),
                now);
        setId(version, id);
        return version;
    }

    private void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
