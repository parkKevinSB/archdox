package com.archdox.cloud.engine.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ArchDoxContextNormalizationServiceTest {
    private final ArchDoxContextNormalizationService service = new ArchDoxContextNormalizationService();

    @Test
    void normalizesExternalFactsIntoCanonicalContext() {
        var context = service.normalize(List.of(
                new ArchDoxContextFact(
                        "buildingUse",
                        "neighborhood living facility",
                        ArchDoxContextFactSource.CUSTOMER_AGENT_EXTRACTED,
                        "document use line",
                        0.90d),
                new ArchDoxContextFact(
                        "structureType",
                        "RC",
                        ArchDoxContextFactSource.CUSTOMER_SYSTEM,
                        "customer PMS constMethod",
                        0.98d),
                new ArchDoxContextFact(
                        "workType",
                        "foundation concrete placement",
                        ArchDoxContextFactSource.USER_PROVIDED,
                        "user answer",
                        0.88d)));

        assertThat(context.values()).containsKeys("buildingUse", "structureType", "workType");
        assertThat(context.values().get("buildingUse").canonicalValue())
                .isEqualTo("NEIGHBORHOOD_LIVING_FACILITY");
        assertThat(context.values().get("structureType").canonicalValue())
                .isEqualTo("REINFORCED_CONCRETE");
        assertThat(context.values().get("workType").canonicalValue())
                .isEqualTo("FOUNDATION_CONCRETE_PLACEMENT");
        assertThat(context.missingQuestions()).isEmpty();
    }

    @Test
    void keepsAmbiguousExternalFactsForFollowUpQuestions() {
        var context = service.normalize(List.of(
                new ArchDoxContextFact(
                        "buildingUse",
                        "neighborhood living facility and business facility",
                        ArchDoxContextFactSource.DOCUMENT_EXTRACTED,
                        "document use line",
                        0.91d)));

        assertThat(context.values().get("buildingUse").canonicalValue()).isEqualTo("MULTI_USE");
        assertThat(context.ambiguities())
                .extracting(ArchDoxContextAmbiguity::fieldName)
                .containsExactly("buildingUse");
        assertThat(context.missingQuestions())
                .extracting(ArchDoxMissingContextQuestion::fieldName)
                .contains("structureType", "workType");
    }
}
