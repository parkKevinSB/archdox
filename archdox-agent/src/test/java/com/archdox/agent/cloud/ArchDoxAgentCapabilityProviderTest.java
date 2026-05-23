package com.archdox.agent.cloud;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.archdox.agent.document.DocumentExportProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArchDoxAgentCapabilityProviderTest {
    @Test
    void advertisesPdfFormatsOnlyWhenLibreOfficeExportIsEnabled() {
        var properties = new DocumentExportProperties();
        var disabled = new ArchDoxAgentCapabilityProvider(properties).capabilities();

        assertFalse(outputFormats(disabled).contains("PDF"));
        assertFalse((Boolean) disabled.get("pdfExport"));

        properties.getLibreOffice().setEnabled(true);
        var enabled = new ArchDoxAgentCapabilityProvider(properties).capabilities();

        assertTrue(outputFormats(enabled).contains("PDF"));
        assertTrue(outputFormats(enabled).contains("DOCX_AND_PDF"));
        assertTrue((Boolean) enabled.get("pdfExport"));
    }

    @SuppressWarnings("unchecked")
    private List<String> outputFormats(java.util.Map<String, Object> capabilities) {
        return (List<String>) capabilities.get("outputFormats");
    }
}
