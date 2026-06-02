package com.archdox.cloud.document.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StandardTemplateFieldCatalogTest {
    private final StandardTemplateFieldCatalog catalog = new StandardTemplateFieldCatalog();

    @Test
    void filtersDailySupervisionFieldsAndPresets() {
        var dailyCatalog = catalog.catalog("daily_supervision");

        assertTrue(hasField(dailyCatalog, "constructionName"));
        assertTrue(hasField(dailyCatalog, "constructionTrade"));
        assertTrue(hasPreset(dailyCatalog, "KOREAN_CONSTRUCTION_DAILY_SUPERVISION_APPENDIX_2"));
        assertFalse(hasPreset(dailyCatalog, "OFFICE_INTERNAL_CONSTRUCTION_DAILY_SUPERVISION"));
        assertFalse(hasField(dailyCatalog, "demolitionWorkerName"));
        assertFalse(hasPreset(dailyCatalog, "KOREAN_DEMOLITION_SAFETY_CHECK_APPENDIX_1"));
    }

    @Test
    void dailySupervisionPresetIsOfficialSubmissionOnly() {
        var dailyCatalog = catalog.catalog("daily_supervision");

        var officialPreset = preset(dailyCatalog, "KOREAN_CONSTRUCTION_DAILY_SUPERVISION_APPENDIX_2");
        assertTrue(officialPreset.templateKind().equals("OFFICIAL_SUBMISSION"));
        assertTrue(officialPreset.customizationPolicy().equals("COPY_AND_OVERRIDE"));
        assertTrue(officialPreset.renderingPolicy().equals("BUNDLED_OFFICIAL_RENDERER"));
        assertFalse(hasPreset(dailyCatalog, "OFFICE_INTERNAL_CONSTRUCTION_DAILY_SUPERVISION"));
    }

    @Test
    void includesCanonicalKoreanDocumentTypeFieldsAndPresets() {
        var constructionDaily = catalog.catalog("construction_daily_supervision_log");
        assertTrue(hasField(constructionDaily, "constructionTrade"));
        assertTrue(hasField(constructionDaily, "assistantArchitectName"));
        assertTrue(hasField(constructionDaily, "correctionResults"));
        assertTrue(hasPreset(constructionDaily, "KOREAN_CONSTRUCTION_DAILY_SUPERVISION_APPENDIX_2"));

        var demolitionSafety = catalog.catalog("demolition_safety_checklist");
        assertTrue(hasField(demolitionSafety, "safetyChecklistItems"));
        assertTrue(hasField(demolitionSafety, "checklistPhotoSummary"));
        assertTrue(hasPreset(demolitionSafety, "KOREAN_DEMOLITION_SAFETY_CHECK_APPENDIX_1"));

        var demolitionDaily = catalog.catalog("demolition_daily_supervision_log");
        assertTrue(hasField(demolitionDaily, "assistantSupervisorName"));
        assertTrue(hasField(demolitionDaily, "specialNotes"));
        assertTrue(hasField(demolitionDaily, "issueAndAction"));
        assertTrue(hasPreset(demolitionDaily, "KOREAN_DEMOLITION_DAILY_SUPERVISION_APPENDIX_2"));
    }

    @Test
    void includesAllFieldsWhenReportTypeIsBlank() {
        var allCatalog = catalog.catalog(null);

        assertTrue(hasField(allCatalog, "constructionName"));
        assertTrue(hasField(allCatalog, "demolitionWorkerName"));
        assertTrue(hasPreset(allCatalog, "KOREAN_CONSTRUCTION_DAILY_SUPERVISION_APPENDIX_2"));
        assertTrue(hasPreset(allCatalog, "KOREAN_DEMOLITION_SAFETY_CHECK_APPENDIX_1"));
    }

    private boolean hasField(StandardTemplateFieldCatalog.TemplateFieldCatalog catalog, String key) {
        return catalog.fields().stream().anyMatch(field -> field.key().equals(key));
    }

    private boolean hasPreset(StandardTemplateFieldCatalog.TemplateFieldCatalog catalog, String code) {
        return catalog.presets().stream().anyMatch(preset -> preset.code().equals(code));
    }

    private StandardTemplateFieldCatalog.TemplateFormPreset preset(
            StandardTemplateFieldCatalog.TemplateFieldCatalog catalog,
            String code
    ) {
        return catalog.presets().stream()
                .filter(preset -> preset.code().equals(code))
                .findFirst()
                .orElseThrow();
    }
}
