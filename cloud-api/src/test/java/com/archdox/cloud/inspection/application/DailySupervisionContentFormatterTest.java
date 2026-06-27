package com.archdox.cloud.inspection.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DailySupervisionContentFormatterTest {
    private static final String COMPLIANT = "\uC801\uD569";
    private static final String NON_COMPLIANT = "\uBD80\uC801\uD569";
    private static final String ACTION_NOTE = "\uC870\uCE58\uC0AC\uD56D";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parentChecklistResultIsRenderedOnTitleLine() {
        var entry = Map.<String, Object>of(
                "inspectionItemCode", "RC_REBAR_CONFIRMATION",
                "inspectionItemName", "Rebar confirmation",
                "checklistRows", List.of(
                        Map.of(
                                "code", "RC_REBAR_CONFIRMATION",
                                "label", "Rebar confirmation",
                                "result", "COMPLIANT"),
                        Map.of(
                                "code", "RC_REBAR_COUNT_DIAMETER_PITCH",
                                "label", "Count, diameter, and pitch confirmed",
                                "result", "COMPLIANT"),
                        Map.of(
                                "code", "RC_REBAR_ANCHORAGE",
                                "label", "Anchorage length confirmed",
                                "result", "NOT_APPLICABLE")));

        assertThat(DailySupervisionContentFormatter.formatEntry(entry))
                .isEqualTo("Rebar confirmation / " + COMPLIANT
                        + "\n- Count, diameter, and pitch confirmed / " + COMPLIANT);
    }

    @Test
    void notApplicableParentOmitsTitleWhenChildRowsRender() {
        var entry = Map.<String, Object>of(
                "inspectionItemCode", "RC_REBAR_CONFIRMATION",
                "inspectionItemName", "Rebar confirmation",
                "checklistRows", List.of(
                        Map.of(
                                "code", "RC_REBAR_CONFIRMATION",
                                "label", "Rebar confirmation",
                                "result", "NOT_APPLICABLE"),
                        Map.of(
                                "code", "RC_REBAR_COUNT_DIAMETER_PITCH",
                                "label", "Count, diameter, and pitch confirmed",
                                "result", "COMPLIANT")));

        assertThat(DailySupervisionContentFormatter.formatEntry(entry))
                .isEqualTo("- Count, diameter, and pitch confirmed / " + COMPLIANT);
    }

    @Test
    void parentOnlyInspectionStillRendersTitleResult() throws Exception {
        var entry = objectMapper.readTree("""
                {
                  "inspectionItemCode": "RC_REBAR_CONFIRMATION",
                  "inspectionItemName": "Rebar confirmation",
                  "checklistRows": [
                    {
                      "code": "RC_REBAR_CONFIRMATION",
                      "label": "Rebar confirmation",
                      "result": "NON_COMPLIANT",
                      "actionNote": "Adjust spacing and recheck"
                    }
                  ]
                }
                """);

        assertThat(DailySupervisionContentFormatter.formatEntry(entry))
                .isEqualTo("Rebar confirmation / " + NON_COMPLIANT
                        + " / " + ACTION_NOTE + ": Adjust spacing and recheck");
    }

    @Test
    void notApplicableOnlyRowsAreNotRendered() {
        var entry = Map.<String, Object>of(
                "inspectionItemCode", "RC_REBAR_CONFIRMATION",
                "inspectionItemName", "Rebar confirmation",
                "checklistRows", List.of(
                        Map.of(
                                "code", "RC_REBAR_CONFIRMATION",
                                "label", "Rebar confirmation",
                                "result", "NOT_APPLICABLE"),
                        Map.of(
                                "code", "RC_REBAR_COUNT_DIAMETER_PITCH",
                                "label", "Count, diameter, and pitch confirmed",
                                "result", "NOT_APPLICABLE")));

        assertThat(DailySupervisionContentFormatter.formatEntry(entry)).isBlank();
    }
}
