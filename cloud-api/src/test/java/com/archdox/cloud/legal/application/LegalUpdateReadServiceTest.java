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
import com.archdox.cloud.legal.infra.LegalArticleDiffRepository;
import com.archdox.cloud.legal.infra.LegalChangeDigestRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class LegalUpdateReadServiceTest {
    private final LegalChangeDigestRepository repository = mock(LegalChangeDigestRepository.class);
    private final LegalArticleDiffRepository articleDiffRepository = mock(LegalArticleDiffRepository.class);
    private final LegalUpdateReadService service = new LegalUpdateReadService(repository, articleDiffRepository);

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

        var updates = service.recent(30, 50);

        assertThat(updates).singleElement()
                .satisfies(update -> {
                    assertThat(update.id()).isEqualTo(10L);
                    assertThat(update.title()).isEqualTo("건축법 조문 변경: 신설 1건");
                    assertThat(update.articleDiffs()).singleElement()
                            .satisfies(diff -> {
                                assertThat(diff.articleNo()).isEqualTo("제25조");
                                assertThat(diff.changeType()).isEqualTo(LegalArticleChangeType.ADDED);
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

    private void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
