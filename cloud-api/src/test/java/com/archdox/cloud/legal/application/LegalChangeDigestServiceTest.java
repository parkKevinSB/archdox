package com.archdox.cloud.legal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.archdox.cloud.legal.domain.LegalAct;
import com.archdox.cloud.legal.domain.LegalArticleChangeType;
import com.archdox.cloud.legal.domain.LegalArticleDiff;
import com.archdox.cloud.legal.domain.LegalChangeDigest;
import com.archdox.cloud.legal.domain.LegalChangeDigestSource;
import com.archdox.cloud.legal.domain.LegalChangeDigestStatus;
import com.archdox.cloud.legal.domain.LegalChangeSet;
import com.archdox.cloud.legal.infra.LegalChangeDigestRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LegalChangeDigestServiceTest {
    private final LegalChangeDigestRepository repository = mock(LegalChangeDigestRepository.class);
    private final LegalChangeDigestService service = new LegalChangeDigestService(repository);

    @Test
    void deterministicDigestWritesReadableConstructionSupervisionSummary() throws Exception {
        var now = OffsetDateTime.parse("2026-06-05T09:00:00+09:00");
        var act = new LegalAct(1L, "BUILDING_ACT", "건축법", "LAW", "KR", "001823", now);
        var changeSet = changeSet(now);
        var diffs = List.of(
                diff(100L, "25", LegalArticleChangeType.MODIFIED, now),
                diff(100L, "25의2", LegalArticleChangeType.ADDED, now));
        when(repository.findByChangeSetId(100L)).thenReturn(Optional.empty());
        when(repository.save(any(LegalChangeDigest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var digest = service.ensureDeterministicDigest(changeSet, act, diffs, now);

        assertThat(digest.title()).isEqualTo("건축법 조문 변경: 신설 1건, 수정 1건");
        assertThat(digest.summary())
                .contains("건축법의 조문 변경 2건")
                .contains("주요 변경 조문: 제25조, 제25의2조")
                .contains("원천 변경 요약: 원천 변경 기록");
        assertThat(digest.impactSummary()).contains("공사감리일지").contains("체크리스트");
        assertThat(digest.affectedReportTypes()).containsExactly(
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "CONSTRUCTION_SUPERVISION_REPORT");
        assertThat(digest.affectedCatalogItems()).containsExactly(
                "CONSTRUCTION_SUPERVISION_CHECKLIST",
                "CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT");
        assertThat(digest.status()).isEqualTo(LegalChangeDigestStatus.PUBLISHED);
        assertThat(digest.source()).isEqualTo(LegalChangeDigestSource.DETERMINISTIC);
    }

    @Test
    void deterministicDigestRefreshesExistingDeterministicDigest() throws Exception {
        var now = OffsetDateTime.parse("2026-06-05T09:00:00+09:00");
        var existing = new LegalChangeDigest(
                100L,
                LegalChangeDigestStatus.PUBLISHED,
                LegalChangeDigestSource.DETERMINISTIC,
                "기존 제목",
                "기존 요약",
                "기존 영향",
                List.of(),
                List.of(),
                null,
                LocalDate.of(2026, 7, 1),
                now,
                now,
                now);
        when(repository.findByChangeSetId(100L)).thenReturn(Optional.of(existing));

        var digest = service.ensureDeterministicDigest(
                changeSet(now),
                new LegalAct(1L, "BUILDING_ACT", "건축법", "LAW", "KR", "001823", now),
                List.of(diff(100L, "25", LegalArticleChangeType.MODIFIED, now)),
                now.plusHours(1));

        assertThat(digest).isSameAs(existing);
        assertThat(digest.title()).isEqualTo("건축법 조문 변경: 수정 1건");
        assertThat(digest.summary()).contains("주요 변경 조문: 제25조");
        assertThat(digest.affectedReportTypes()).containsExactly(
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "CONSTRUCTION_SUPERVISION_REPORT");
        assertThat(digest.publishedAt()).isEqualTo(now);
        assertThat(digest.updatedAt()).isEqualTo(now.plusHours(1));
        verify(repository, never()).save(any());
    }

    @Test
    void deterministicDigestDoesNotOverwriteAiDigest() throws Exception {
        var now = OffsetDateTime.parse("2026-06-05T09:00:00+09:00");
        var existing = new LegalChangeDigest(
                100L,
                LegalChangeDigestStatus.PUBLISHED,
                LegalChangeDigestSource.AI,
                "AI 제목",
                "AI 요약",
                "AI 영향",
                List.of("AI_REPORT"),
                List.of("AI_CATALOG"),
                "run-1",
                LocalDate.of(2026, 7, 1),
                now,
                now,
                now);
        when(repository.findByChangeSetId(100L)).thenReturn(Optional.of(existing));

        var digest = service.ensureDeterministicDigest(
                changeSet(now),
                new LegalAct(1L, "BUILDING_ACT", "건축법", "LAW", "KR", "001823", now),
                List.of(diff(100L, "25", LegalArticleChangeType.MODIFIED, now)),
                now.plusHours(1));

        assertThat(digest).isSameAs(existing);
        assertThat(digest.title()).isEqualTo("AI 제목");
        assertThat(digest.summary()).isEqualTo("AI 요약");
        assertThat(digest.affectedReportTypes()).containsExactly("AI_REPORT");
        verify(repository, never()).save(any());
    }

    private LegalChangeSet changeSet(OffsetDateTime now) throws Exception {
        var changeSet = new LegalChangeSet(
                1L,
                10L,
                20L,
                30L,
                LocalDate.of(2026, 7, 1),
                "원천 변경 기록",
                Map.of(),
                now);
        setId(changeSet, 100L);
        return changeSet;
    }

    private LegalArticleDiff diff(
            Long changeSetId,
            String articleNo,
            LegalArticleChangeType changeType,
            OffsetDateTime now
    ) {
        return new LegalArticleDiff(
                changeSetId,
                10L,
                "ARTICLE_" + articleNo,
                articleNo,
                changeType,
                1L,
                2L,
                "before",
                "after",
                "diff",
                now);
    }

    private void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
