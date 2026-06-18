package com.archdox.cloud.inspection.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DailySupervisionContentFormatterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parentChecklistResultIsRenderedOnTitleLine() {
        var entry = Map.<String, Object>of(
                "inspectionItemCode", "RC_REBAR_CONFIRMATION",
                "inspectionItemName", "철근배근의 확인사항",
                "checklistRows", List.of(
                        Map.of(
                                "code", "RC_REBAR_CONFIRMATION",
                                "label", "철근배근의 확인사항",
                                "result", "COMPLIANT"),
                        Map.of(
                                "code", "RC_REBAR_COUNT_DIAMETER_PITCH",
                                "label", "개수, 철근지름, 피치 확인",
                                "result", "COMPLIANT"),
                        Map.of(
                                "code", "RC_REBAR_ANCHORAGE",
                                "label", "정착길이와 굽힘정착 깊이 확인",
                                "result", "NOT_APPLICABLE")));

        assertThat(DailySupervisionContentFormatter.formatEntry(entry))
                .isEqualTo("""
                        철근배근의 확인사항 / 적합
                        - 개수, 철근지름, 피치 확인 / 적합""");
    }

    @Test
    void notApplicableParentKeepsTitleWithoutStatus() {
        var entry = Map.<String, Object>of(
                "inspectionItemCode", "RC_REBAR_CONFIRMATION",
                "inspectionItemName", "철근배근의 확인사항",
                "checklistRows", List.of(
                        Map.of(
                                "code", "RC_REBAR_CONFIRMATION",
                                "label", "철근배근의 확인사항",
                                "result", "NOT_APPLICABLE"),
                        Map.of(
                                "code", "RC_REBAR_COUNT_DIAMETER_PITCH",
                                "label", "개수, 철근지름, 피치 확인",
                                "result", "COMPLIANT")));

        assertThat(DailySupervisionContentFormatter.formatEntry(entry))
                .isEqualTo("""
                        철근배근의 확인사항
                        - 개수, 철근지름, 피치 확인 / 적합""");
    }

    @Test
    void parentOnlyInspectionStillRendersTitleResult() throws Exception {
        var entry = objectMapper.readTree("""
                {
                  "inspectionItemCode": "RC_REBAR_CONFIRMATION",
                  "inspectionItemName": "철근배근의 확인사항",
                  "checklistRows": [
                    {
                      "code": "RC_REBAR_CONFIRMATION",
                      "label": "철근배근의 확인사항",
                      "result": "NON_COMPLIANT",
                      "actionNote": "피치 조정 후 재확인"
                    }
                  ]
                }
                """);

        assertThat(DailySupervisionContentFormatter.formatEntry(entry))
                .isEqualTo("철근배근의 확인사항 / 부적합 / 조치사항: 피치 조정 후 재확인");
    }

    @Test
    void notApplicableOnlyRowsAreNotRendered() {
        var entry = Map.<String, Object>of(
                "inspectionItemCode", "RC_REBAR_CONFIRMATION",
                "inspectionItemName", "철근배근의 확인사항",
                "checklistRows", List.of(
                        Map.of(
                                "code", "RC_REBAR_CONFIRMATION",
                                "label", "철근배근의 확인사항",
                                "result", "NOT_APPLICABLE"),
                        Map.of(
                                "code", "RC_REBAR_COUNT_DIAMETER_PITCH",
                                "label", "개수, 철근지름, 피치 확인",
                                "result", "NOT_APPLICABLE")));

        assertThat(DailySupervisionContentFormatter.formatEntry(entry)).isBlank();
    }
}
