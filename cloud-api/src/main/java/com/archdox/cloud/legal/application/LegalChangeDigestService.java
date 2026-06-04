package com.archdox.cloud.legal.application;

import com.archdox.cloud.legal.domain.LegalAct;
import com.archdox.cloud.legal.domain.LegalArticleDiff;
import com.archdox.cloud.legal.domain.LegalArticleChangeType;
import com.archdox.cloud.legal.domain.LegalChangeDigest;
import com.archdox.cloud.legal.domain.LegalChangeDigestSource;
import com.archdox.cloud.legal.domain.LegalChangeDigestStatus;
import com.archdox.cloud.legal.domain.LegalChangeSet;
import com.archdox.cloud.legal.infra.LegalChangeDigestRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegalChangeDigestService {
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
        return repository.findByChangeSetId(changeSet.id())
                .orElseGet(() -> repository.save(new LegalChangeDigest(
                        changeSet.id(),
                        LegalChangeDigestStatus.PUBLISHED,
                        LegalChangeDigestSource.DETERMINISTIC,
                        title(act, diffs),
                        summary(changeSet, diffs),
                        impactSummary(),
                        List.of(),
                        List.of(),
                        null,
                        changeSet.effectiveDate(),
                        changeSet.detectedAt(),
                        now,
                        now)));
    }

    private String title(LegalAct act, List<LegalArticleDiff> diffs) {
        var actName = act == null ? "법령" : act.actName();
        if (diffs == null || diffs.isEmpty()) {
            return actName + " 변경사항이 감지되었습니다";
        }
        var counts = diffs.stream()
                .collect(Collectors.groupingBy(LegalArticleDiff::changeType, Collectors.counting()));
        return actName + " " + changeSummary(counts);
    }

    private String changeSummary(Map<LegalArticleChangeType, Long> counts) {
        var added = counts.getOrDefault(LegalArticleChangeType.ADDED, 0L);
        var modified = counts.getOrDefault(LegalArticleChangeType.MODIFIED, 0L);
        var removed = counts.getOrDefault(LegalArticleChangeType.REMOVED, 0L);
        var parts = new java.util.ArrayList<String>();
        if (added > 0) {
            parts.add("신설 " + added + "건");
        }
        if (modified > 0) {
            parts.add("수정 " + modified + "건");
        }
        if (removed > 0) {
            parts.add("삭제 " + removed + "건");
        }
        return parts.isEmpty() ? "변경사항" : String.join(", ", parts);
    }

    private String summary(LegalChangeSet changeSet, List<LegalArticleDiff> diffs) {
        var count = diffs == null ? 0 : diffs.size();
        var examples = diffs == null ? List.<String>of() : diffs.stream()
                .limit(5)
                .map(diff -> diff.articleNo() == null ? diff.articleKey() : diff.articleNo())
                .toList();
        var suffix = examples.isEmpty() ? "" : " 주요 조문: " + String.join(", ", examples) + ".";
        return changeSet.summary() + " 감지된 조문 변경은 " + count + "건입니다." + suffix;
    }

    private String impactSummary() {
        return "공사감리 업무 영향도는 아직 자동 확정하지 않았습니다. 운영자는 원문 diff를 확인하고, 필요한 경우 공사감리 체크리스트/문서 템플릿 반영 여부를 검토해야 합니다.";
    }
}
