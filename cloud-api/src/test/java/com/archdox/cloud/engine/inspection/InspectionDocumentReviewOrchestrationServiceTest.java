package com.archdox.cloud.engine.inspection;

import static org.assertj.core.api.Assertions.assertThat;

import com.archdox.cloud.engine.application.ArchDoxEngineResultStatus;
import com.archdox.cloud.engine.dto.EngineReviewSessionResponse;
import com.archdox.cloud.engine.dto.EngineValidationResultResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InspectionDocumentReviewOrchestrationServiceTest {
    private final InspectionDocumentReviewOrchestrationService service =
            new InspectionDocumentReviewOrchestrationService(null, null, null, null);

    @Test
    void targetDateMismatchOverridesPassValidation() {
        var validation = new EngineReviewSessionResponse(
                "rvw_sess_test",
                "COMPLETED",
                "project",
                "preflight",
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "daily-log.pdf",
                List.of(),
                Map.of("catalogSelections", List.of(Map.of("inspectionItemCode", "FOUNDATION_LEVEL"))),
                new EngineValidationResultResponse(
                        "eng_run_test",
                        ArchDoxEngineResultStatus.PASS,
                        true,
                        "Context is ready.",
                        List.of(),
                        List.of(),
                        List.of(),
                        "ALLOW",
                        List.of(),
                        "VALIDATION",
                        Map.of()),
                null,
                null,
                null,
                null);

        var result = service.output(
                "rvw_sess_test",
                "CONSTRUCTION_DAILY_SUPERVISION_LOG",
                "2021-01-28",
                Map.of(
                        "targetDate", "2021-01-28",
                        "targetDateMatched", false,
                        "availableDates", List.of("2021-01-26", "2021-01-29")),
                validation,
                validation);

        assertThat(result)
                .containsEntry("workflowStatus", "DATE_NOT_FOUND")
                .containsEntry("targetDate", "2021-01-28");
        assertThat(result.get("questions"))
                .asList()
                .hasSize(1)
                .first()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("fieldName", "targetDate")
                .containsEntry("blocking", true);
        assertThat(result.get("nextActions"))
                .asList()
                .first()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("code", "CHOOSE_AVAILABLE_DATE")
                .containsEntry("blocking", true);
        assertThat(result.get("contextSummary"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("generationAllowed", false)
                .containsEntry("engineStatus", "WARN")
                .containsEntry("targetDateMatched", false)
                .containsEntry("availableDateCount", 2);
        assertThat(result.get("validationResult"))
                .isInstanceOfSatisfying(EngineValidationResultResponse.class, validationResult -> {
                    assertThat(validationResult.status()).isEqualTo(ArchDoxEngineResultStatus.WARN);
                    assertThat(validationResult.generationAllowed()).isFalse();
                    assertThat(validationResult.findings())
                            .extracting(finding -> finding.code())
                            .containsExactly("TARGET_DATE_NOT_FOUND");
                    assertThat(validationResult.metadata())
                            .containsEntry("requestedTargetDate", "2021-01-28")
                            .containsEntry("availableDates", List.of("2021-01-26", "2021-01-29"));
                });
    }
}
