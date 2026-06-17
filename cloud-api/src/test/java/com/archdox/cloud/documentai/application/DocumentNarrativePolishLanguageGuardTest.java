package com.archdox.cloud.documentai.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentNarrativePolishLanguageGuardTest {
    @Test
    void japaneseReasonIsReplacedWithKoreanReason() {
        var reason = DocumentNarrativePolishLanguageGuard.sanitizeAiReason(
                "箇条書きを 자연스러운 문장으로 연결하여 공식 보고서에 적합하도록 문장 완성");

        assertThat(reason).isEqualTo("보고서 문체로 다듬었습니다.");
    }

    @Test
    void japaneseTextIsDetected() {
        assertThat(DocumentNarrativePolishLanguageGuard.containsJapaneseText("箇条書きを 자연스럽게 연결")).isTrue();
        assertThat(DocumentNarrativePolishLanguageGuard.containsJapaneseText("항목형 문장을 자연스럽게 연결했습니다.")).isFalse();
    }
}
