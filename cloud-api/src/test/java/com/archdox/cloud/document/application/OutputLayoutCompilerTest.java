package com.archdox.cloud.document.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutputLayoutCompilerTest {
    private final OutputLayoutCompiler compiler = new OutputLayoutCompiler();

    @Test
    void compilesPhotoTableSectionIntoLayoutAndTemplateField() {
        var snapshot = Map.<String, Object>of(
                "photos", List.of(Map.of(
                        "photoId", 10,
                        "stepCode", "PHOTOS",
                        "workingStorageRef", "photos/10-working.webp")),
                "checklistAnswers", List.of());
        var layout = Map.<String, Object>of("sections", List.of(Map.of(
                "key", "photoSection",
                "type", "PHOTO_TABLE",
                "title", "Photo Section",
                "photosPerRow", 2,
                "fields", List.of(
                        Map.of("label", "Photo", "source", "photoId"),
                        Map.of("label", "Step", "source", "stepCode")))));

        var binding = compiler.compile(layout, snapshot);
        var section = asMap(binding.sections().get("photoSection"));

        assertEquals("PHOTO_TABLE", section.get("type"));
        assertEquals(2, section.get("photosPerRow"));
        assertEquals(1, section.get("itemCount"));
        assertTrue(String.valueOf(binding.templateFields().get("photoSection")).contains("Photo: 10"));
    }

    @Test
    void compilesValueSectionFromSnapshotPath() {
        var snapshot = Map.<String, Object>of(
                "steps", Map.of("BASIC_INFO", Map.of("payload", Map.of("weather", "Clear"))));
        var layout = Map.<String, Object>of("sections", List.of(Map.of(
                "key", "weatherSection",
                "type", "VALUE",
                "title", "Weather",
                "valueLabel", "Weather",
                "source", "steps.BASIC_INFO.payload.weather")));

        var binding = compiler.compile(layout, snapshot);

        assertEquals("Weather\nWeather: Clear", binding.templateFields().get("weatherSection"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
