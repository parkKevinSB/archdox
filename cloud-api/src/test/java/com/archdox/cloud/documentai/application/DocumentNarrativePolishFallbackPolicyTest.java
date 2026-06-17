package com.archdox.cloud.documentai.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.archdox.cloud.documentai.dto.DocumentNarrativePolishResponse;
import com.archdox.documentai.NarrativePolishField;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentNarrativePolishFallbackPolicyTest {
    @Test
    void terseNoIssuePhrasesArePolishedForReportGeneration() {
        var fields = List.of(
                new NarrativePolishField("steps.REMARKS.payload.issueAndAction", "지적사항", "지적사항 없음"),
                new NarrativePolishField("steps.REMARKS.payload.nextAction", "다음 조치", "다음 조치 없음"),
                new NarrativePolishField("steps.REMARKS.payload.issueAndAction", "지적사항 및 처리결과", "특기사항 없이 좋음"));

        var suggestions = DocumentNarrativePolishFallbackPolicy.supplement(fields, List.of());

        assertThat(suggestions)
                .extracting(DocumentNarrativePolishResponse.SuggestionResponse::polishedText)
                .containsExactly(
                        "지적사항이 없습니다.",
                        "추가 조치 사항이 없습니다.",
                        "특기사항이 없습니다.");
        assertThat(suggestions).allMatch(DocumentNarrativePolishResponse.SuggestionResponse::applicable);
    }

    @Test
    void existingApplicableAiSuggestionIsNotOverwritten() {
        var path = "steps.REMARKS.payload.issueAndAction";
        var fields = List.of(new NarrativePolishField(path, "지적사항", "지적사항 없음"));
        var aiSuggestion = new DocumentNarrativePolishResponse.SuggestionResponse(
                path,
                "지적사항",
                "지적사항 없음",
                "지적사항은 별도로 확인되지 않았습니다.",
                "AI",
                "HIGH",
                true);

        var suggestions = DocumentNarrativePolishFallbackPolicy.supplement(fields, List.of(aiSuggestion));

        assertThat(suggestions).containsExactly(aiSuggestion);
    }

    @Test
    void specialNoteNoIssuePhraseIsShortAndDirect() {
        var fields = List.of(new NarrativePolishField("steps.REMARKS.payload.issueAndAction", "지적사항 및 처리결과", "특기사항 없이 좋음"));

        var suggestions = DocumentNarrativePolishFallbackPolicy.supplement(fields, List.of());

        assertThat(suggestions)
                .extracting(DocumentNarrativePolishResponse.SuggestionResponse::polishedText)
                .containsExactly("특기사항이 없습니다.");
    }
}
