package com.archdox.cloud.agent.application;

import com.archdox.cloud.agent.domain.ArchDoxAgent;
import com.archdox.document.OutputFormat;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class ArchDoxAgentCapabilities {
    private ArchDoxAgentCapabilities() {
    }

    static boolean supportsDocumentRender(ArchDoxAgent agent, OutputFormat outputFormat) {
        if (agent == null || outputFormat == null) {
            return false;
        }
        return supportsDocumentRender(agent.capabilitiesJson(), outputFormat);
    }

    static boolean supportsDocumentRender(Map<String, Object> capabilities, OutputFormat outputFormat) {
        if (capabilities == null || capabilities.isEmpty() || outputFormat == null) {
            return false;
        }
        if (!bool(capabilities, "documentGeneration") && !bool(capabilities, "documentRender")) {
            return false;
        }

        var outputFormats = stringSet(capabilities.get("outputFormats"));
        if (outputFormats.contains(outputFormat.name())) {
            return true;
        }
        if (outputFormats.isEmpty()) {
            return outputFormat == OutputFormat.DOCX || outputFormat == OutputFormat.HTML;
        }
        return switch (outputFormat) {
            case DOCX -> outputFormats.contains("DOCX");
            case HTML -> outputFormats.contains("HTML");
            case PDF -> outputFormats.contains("PDF") || bool(capabilities, "pdfExport");
            case DOCX_AND_PDF -> outputFormats.contains("DOCX_AND_PDF")
                    || (outputFormats.contains("DOCX") && (outputFormats.contains("PDF") || bool(capabilities, "pdfExport")));
            case HTML_AND_PDF -> outputFormats.contains("HTML_AND_PDF")
                    || (outputFormats.contains("HTML") && (outputFormats.contains("PDF") || bool(capabilities, "pdfExport")));
            case HWP -> outputFormats.contains("HWP") || bool(capabilities, "hwpExport");
            case HWPX -> outputFormats.contains("HWPX") || bool(capabilities, "hwpxExport");
        };
    }

    private static boolean bool(Map<String, Object> capabilities, String key) {
        var value = capabilities.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return false;
    }

    private static Set<String> stringSet(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return Set.of();
        }
        return collection.stream()
                .filter(item -> item != null && !String.valueOf(item).isBlank())
                .map(item -> String.valueOf(item).trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
