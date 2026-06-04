package com.archdox.cloud.legal.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.archdox.cloud.legal.domain.LegalArticleChangeType;
import com.archdox.cloud.legal.domain.LegalArticleVersion;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegalDiffServiceTest {
    private final LegalDiffService diffService = new LegalDiffService();

    @Test
    void detectsAddedModifiedAndRemovedArticles() {
        var now = OffsetDateTime.now();
        var previous = List.of(
                version(1L, 10L, "A1", "1", "Purpose", "hash:a", now),
                version(2L, 10L, "A2", "2", "Evidence", "hash:b", now),
                version(3L, 10L, "A3", "3", "Removed", "hash:c", now));
        var current = List.of(
                version(1L, 20L, "A1", "1", "Purpose", "hash:a", now),
                version(2L, 20L, "A2", "2", "Evidence", "hash:b2", now),
                version(4L, 20L, "A4", "4", "Added", "hash:d", now));

        var diffs = diffService.diff(previous, current);

        assertThat(diffs).hasSize(3);
        assertThat(diffs).extracting(LegalArticleDiffDraft::changeType)
                .containsExactly(
                        LegalArticleChangeType.MODIFIED,
                        LegalArticleChangeType.REMOVED,
                        LegalArticleChangeType.ADDED);
    }

    private LegalArticleVersion version(
            Long articleId,
            Long versionId,
            String articleKey,
            String articleNo,
            String title,
            String hash,
            OffsetDateTime now
    ) {
        return new LegalArticleVersion(
                articleId,
                versionId,
                articleKey,
                articleNo,
                title,
                "text",
                "text",
                hash,
                null,
                Map.of(),
                now);
    }
}
