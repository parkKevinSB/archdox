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
        assertFalse(hasField(dailyCatalog, "demolitionWorkerName"));
        assertFalse(hasPreset(dailyCatalog, "KOREAN_DEMOLITION_SAFETY_CHECK_APPENDIX_1"));
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
}
