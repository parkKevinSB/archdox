package com.archdox.cloud.documentai.application;

import com.archdox.cloud.documentai.dto.DocumentNarrativePolishResponse;
import com.archdox.documentai.NarrativePolishField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class DocumentNarrativePolishFallbackPolicy {
    private DocumentNarrativePolishFallbackPolicy() {
    }

    static List<DocumentNarrativePolishResponse.SuggestionResponse> supplement(
            List<NarrativePolishField> fields,
            List<DocumentNarrativePolishResponse.SuggestionResponse> aiSuggestions
    ) {
        var result = new ArrayList<>(aiSuggestions);
        for (var field : fields) {
            if (hasApplicableSuggestion(field.path(), aiSuggestions)) {
                continue;
            }
            fallbackText(field.originalText()).ifPresent(polished ->
                    result.add(new DocumentNarrativePolishResponse.SuggestionResponse(
                            field.path(),
                            field.label(),
                            field.originalText(),
                            polished,
                            "짧은 현장 입력을 보고서 문체로 정리",
                            "MEDIUM",
                            true)));
        }
        return List.copyOf(result);
    }

    private static boolean hasApplicableSuggestion(
            String path,
            List<DocumentNarrativePolishResponse.SuggestionResponse> suggestions
    ) {
        return suggestions.stream()
                .anyMatch(suggestion -> path.equals(suggestion.path()) && suggestion.applicable());
    }

    static Optional<String> fallbackText(String raw) {
        var text = normalize(raw);
        if (text.isBlank()) {
            return Optional.empty();
        }

        var lower = text.toLowerCase(Locale.ROOT);
        if (compactEquals(text, "지적사항 없음")) {
            return Optional.of("지적사항은 확인되지 않았습니다.");
        }
        if (compactEquals(text, "다음 조치 없음") || compactEquals(text, "추가 조치 없음")) {
            return Optional.of("추가 조치 사항은 없습니다.");
        }
        if (compactEquals(text, "특기사항 없음")) {
            return Optional.of("특기사항은 확인되지 않았습니다.");
        }
        if (lower.contains("확인시") && lower.contains("이상 없음")) {
            var subject = text
                    .replace("확인시", "")
                    .replace("이상 없음", "")
                    .trim();
            if (!subject.isBlank()) {
                return Optional.of(subject + objectParticle(subject) + " 확인한 결과, 이상이 없음을 확인하였습니다.");
            }
        }
        if (lower.endsWith("이상 없음")) {
            var subject = text.substring(0, text.length() - "이상 없음".length()).trim();
            if (!subject.isBlank()) {
                return Optional.of(subject + "에 이상이 없음을 확인하였습니다.");
            }
        }
        if (lower.endsWith("확인함")) {
            var subject = text.substring(0, text.length() - "확인함".length()).trim();
            if (!subject.isBlank()) {
                return Optional.of(subject + objectParticle(subject) + " 확인하였습니다.");
            }
        }
        if (lower.endsWith("양호")) {
            var subject = text.substring(0, text.length() - "양호".length()).trim();
            if (!subject.isBlank()) {
                return Optional.of(subject + " 상태가 양호함을 확인하였습니다.");
            }
        }
        return Optional.empty();
    }

    private static boolean compactEquals(String text, String expected) {
        return text.replace(" ", "").equals(expected.replace(" ", ""));
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }

    private static String objectParticle(String text) {
        if (text.isBlank()) {
            return "을";
        }
        var last = text.charAt(text.length() - 1);
        if (last >= 0xAC00 && last <= 0xD7A3) {
            return ((last - 0xAC00) % 28) == 0 ? "를" : "을";
        }
        return "을";
    }
}
