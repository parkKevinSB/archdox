package com.archdox.cloud.legal.application;

import com.archdox.cloud.legal.domain.LegalAct;
import com.archdox.cloud.legal.domain.LegalArticleChangeType;
import com.archdox.cloud.legal.domain.LegalArticleDiff;
import com.archdox.cloud.legal.domain.LegalChangeDigest;
import com.archdox.cloud.legal.domain.LegalChangeDigestSource;
import com.archdox.cloud.legal.domain.LegalChangeDigestStatus;
import com.archdox.cloud.legal.domain.LegalChangeSet;
import com.archdox.cloud.legal.infra.LegalChangeDigestRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegalChangeDigestService {
    private static final int ARTICLE_EXAMPLE_LIMIT = 5;
    private static final List<String> CONSTRUCTION_SUPERVISION_REPORT_TYPES = List.of(
            "CONSTRUCTION_DAILY_SUPERVISION_LOG",
            "CONSTRUCTION_SUPERVISION_REPORT");
    private static final List<String> CONSTRUCTION_SUPERVISION_CATALOG_ITEMS = List.of(
            "CONSTRUCTION_SUPERVISION_CHECKLIST",
            "CONSTRUCTION_SUPERVISION_LEGAL_CONTEXT");

    private final LegalChangeDigestRepository repository;

    public LegalChangeDigestService(LegalChangeDigestRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public LegalChangeDigest ensureDeterministicDigest(
            LegalChangeSet changeSet,
            LegalAct act,
            List<LegalArticleDiff> diffs,
            OffsetDateTime now
    ) {
        var title = title(act, diffs);
        var summary = summary(changeSet, act, diffs);
        var impactSummary = impactSummary(act);
        var affectedReportTypes = affectedReportTypes(act);
        var affectedCatalogItems = affectedCatalogItems(act);
        return repository.findByChangeSetId(changeSet.id())
                .map(existing -> {
                    existing.refreshDeterministic(
                            title,
                            summary,
                            impactSummary,
                            affectedReportTypes,
                            affectedCatalogItems,
                            changeSet.effectiveDate(),
                            changeSet.detectedAt(),
                            now);
                    return existing;
                })
                .orElseGet(() -> repository.save(new LegalChangeDigest(
                        changeSet.id(),
                        LegalChangeDigestStatus.PUBLISHED,
                        LegalChangeDigestSource.DETERMINISTIC,
                        title,
                        summary,
                        impactSummary,
                        affectedReportTypes,
                        affectedCatalogItems,
                        null,
                        changeSet.effectiveDate(),
                        changeSet.detectedAt(),
                        now,
                        now)));
    }

    private String title(LegalAct act, List<LegalArticleDiff> diffs) {
        var actName = actName(act);
        var safeDiffs = safeDiffs(diffs);
        if (safeDiffs.isEmpty()) {
            return actName + " 변경사항 감지";
        }
        return actName + " 조문 변경: " + changeSummary(changeCounts(safeDiffs));
    }

    private String summary(LegalChangeSet changeSet, LegalAct act, List<LegalArticleDiff> diffs) {
        var safeDiffs = safeDiffs(diffs);
        var count = safeDiffs.size();
        var result = new StringBuilder();
        if (count == 0) {
            result.append("법령 동기화에서 ").append(actName(act)).append(" 변경 원천을 기록했습니다. 조문 단위 diff는 없습니다.");
        } else {
            result.append("법령 동기화에서 ")
                    .append(actName(act))
                    .append("의 조문 변경 ")
                    .append(count)
                    .append("건을 감지했습니다.");
            var examples = articleLabels(safeDiffs);
            if (!examples.isEmpty()) {
                result.append(" 주요 변경 조문: ").append(String.join(", ", examples)).append(".");
            }
        }
        var sourceSummary = blankToNull(changeSet.summary());
        if (sourceSummary != null) {
            result.append(" 원천 변경 요약: ").append(sourceSummary);
        }
        return result.toString();
    }

    private String impactSummary(LegalAct act) {
        if (isConstructionSupervisionRelevant(act)) {
            return "공사감리일지와 감리보고서의 작성 기준, 체크리스트 근거, 현장 증빙 요구사항에 영향을 줄 수 있습니다. "
                    + "변경 조문을 확인하고 필요한 경우 템플릿, 카탈로그, 검토 기준 반영 여부를 점검하세요.";
        }
        return "업무 영향은 아직 자동 확정되지 않았습니다. 변경 조문과 시행일을 확인하고 관련 템플릿, 카탈로그, 검토 기준 반영 여부를 점검하세요.";
    }

    private List<String> affectedReportTypes(LegalAct act) {
        return isConstructionSupervisionRelevant(act) ? CONSTRUCTION_SUPERVISION_REPORT_TYPES : List.of();
    }

    private List<String> affectedCatalogItems(LegalAct act) {
        return isConstructionSupervisionRelevant(act) ? CONSTRUCTION_SUPERVISION_CATALOG_ITEMS : List.of();
    }

    private Map<LegalArticleChangeType, Long> changeCounts(List<LegalArticleDiff> diffs) {
        return diffs.stream()
                .collect(Collectors.groupingBy(LegalArticleDiff::changeType, Collectors.counting()));
    }

    private String changeSummary(Map<LegalArticleChangeType, Long> counts) {
        var added = counts.getOrDefault(LegalArticleChangeType.ADDED, 0L);
        var modified = counts.getOrDefault(LegalArticleChangeType.MODIFIED, 0L);
        var removed = counts.getOrDefault(LegalArticleChangeType.REMOVED, 0L);
        var parts = new ArrayList<String>();
        if (added > 0) {
            parts.add("신설 " + added + "건");
        }
        if (modified > 0) {
            parts.add("수정 " + modified + "건");
        }
        if (removed > 0) {
            parts.add("삭제 " + removed + "건");
        }
        return parts.isEmpty() ? "변경사항 없음" : String.join(", ", parts);
    }

    private List<String> articleLabels(List<LegalArticleDiff> diffs) {
        return diffs.stream()
                .map(this::articleLabel)
                .filter(label -> label != null && !label.isBlank())
                .distinct()
                .limit(ARTICLE_EXAMPLE_LIMIT)
                .toList();
    }

    private String articleLabel(LegalArticleDiff diff) {
        var raw = blankToNull(diff.articleNo());
        if (raw == null) {
            raw = blankToNull(diff.articleKey());
        }
        if (raw == null) {
            return null;
        }
        if (raw.startsWith("제") || raw.contains("조")) {
            return raw;
        }
        if (raw.matches("[0-9]+(의[0-9]+)?")) {
            return "제" + raw + "조";
        }
        return raw;
    }

    private String actName(LegalAct act) {
        var actName = act == null ? null : blankToNull(act.actName());
        return actName == null ? "법령" : actName;
    }

    private boolean isConstructionSupervisionRelevant(LegalAct act) {
        if (act == null) {
            return false;
        }
        var text = String.join(" ",
                        nullToEmpty(act.actCode()),
                        nullToEmpty(act.actName()),
                        nullToEmpty(act.actType()))
                .toLowerCase(Locale.ROOT);
        return text.contains("building")
                || text.contains("construction")
                || text.contains("supervision")
                || text.contains("건축")
                || text.contains("감리");
    }

    private List<LegalArticleDiff> safeDiffs(List<LegalArticleDiff> diffs) {
        return diffs == null ? List.of() : diffs;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
