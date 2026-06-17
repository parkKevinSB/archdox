package com.archdox.cloud.documentai.application;

import java.util.regex.Pattern;

final class DocumentNarrativePolishLanguageGuard {
    private static final String DEFAULT_AI_REASON = "보고서 문체로 다듬었습니다.";
    private static final String UNSAFE_AI_TEXT_REASON = "AI 문장에 한국어가 아닌 표현이 섞여 적용하지 않았습니다.";
    private static final Pattern JAPANESE_KANA = Pattern.compile("[\\p{InHiragana}\\p{InKatakana}]");
    private static final Pattern JAPANESE_MARKER = Pattern.compile("(箇条書き|下さい|ください|です|ます)");

    private DocumentNarrativePolishLanguageGuard() {
    }

    static String sanitizeAiReason(String reason) {
        var text = text(reason);
        if (text.isBlank() || containsJapaneseText(text)) {
            return DEFAULT_AI_REASON;
        }
        return text;
    }

    static boolean containsJapaneseText(String value) {
        var text = text(value);
        return !text.isBlank() && (JAPANESE_KANA.matcher(text).find() || JAPANESE_MARKER.matcher(text).find());
    }

    static String unsafeAiTextReason() {
        return UNSAFE_AI_TEXT_REASON;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
