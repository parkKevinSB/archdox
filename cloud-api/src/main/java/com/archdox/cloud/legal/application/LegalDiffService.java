package com.archdox.cloud.legal.application;

import com.archdox.cloud.legal.domain.LegalArticleChangeType;
import com.archdox.cloud.legal.domain.LegalArticleVersion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class LegalDiffService {
    public List<LegalArticleDiffDraft> diff(
            List<LegalArticleVersion> previous,
            List<LegalArticleVersion> current
    ) {
        var previousByKey = byKey(previous);
        var currentByKey = byKey(current);
        var keys = new LinkedHashSet<String>();
        keys.addAll(previousByKey.keySet());
        keys.addAll(currentByKey.keySet());

        var diffs = new ArrayList<LegalArticleDiffDraft>();
        for (var key : keys) {
            var before = previousByKey.get(key);
            var after = currentByKey.get(key);
            if (before == null && after != null) {
                diffs.add(new LegalArticleDiffDraft(
                        LegalArticleChangeType.ADDED,
                        after.articleId(),
                        after.articleKey(),
                        after.articleNo(),
                        null,
                        after.id(),
                        null,
                        after.contentHash(),
                        "Article added: " + label(after.articleNo(), after.articleTitle())));
            } else if (before != null && after == null) {
                diffs.add(new LegalArticleDiffDraft(
                        LegalArticleChangeType.REMOVED,
                        before.articleId(),
                        before.articleKey(),
                        before.articleNo(),
                        before.id(),
                        null,
                        before.contentHash(),
                        null,
                        "Article removed: " + label(before.articleNo(), before.articleTitle())));
            } else if (before != null && after != null && !before.contentHash().equals(after.contentHash())) {
                diffs.add(new LegalArticleDiffDraft(
                        LegalArticleChangeType.MODIFIED,
                        after.articleId(),
                        after.articleKey(),
                        after.articleNo(),
                        before.id(),
                        after.id(),
                        before.contentHash(),
                        after.contentHash(),
                        "Article modified: " + label(after.articleNo(), after.articleTitle())));
            }
        }
        return diffs;
    }

    private Map<String, LegalArticleVersion> byKey(List<LegalArticleVersion> versions) {
        return versions == null ? Map.of() : versions.stream()
                .sorted(Comparator.comparing(LegalArticleVersion::articleKey))
                .collect(Collectors.toMap(
                        LegalArticleVersion::articleKey,
                        Function.identity(),
                        (left, right) -> right));
    }

    private String label(String articleNo, String articleTitle) {
        var no = articleNo == null ? "" : articleNo;
        var title = articleTitle == null || articleTitle.isBlank() ? "" : " " + articleTitle.trim();
        return (no + title).trim();
    }
}
