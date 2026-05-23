package com.archdox.cloud.agent.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.archdox.document.OutputFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArchDoxAgentCapabilitiesTest {
    @Test
    void legacyDocumentGenerationAgentSupportsDocxAndHtmlOnly() {
        var capabilities = Map.<String, Object>of("documentGeneration", true);

        assertTrue(ArchDoxAgentCapabilities.supportsDocumentRender(capabilities, OutputFormat.DOCX));
        assertTrue(ArchDoxAgentCapabilities.supportsDocumentRender(capabilities, OutputFormat.HTML));
        assertFalse(ArchDoxAgentCapabilities.supportsDocumentRender(capabilities, OutputFormat.PDF));
    }

    @Test
    void pdfCapabilityEnablesPdfRelatedOutputs() {
        var capabilities = Map.<String, Object>of(
                "documentGeneration", true,
                "pdfExport", true,
                "outputFormats", List.of("DOCX", "HTML", "PDF"));

        assertTrue(ArchDoxAgentCapabilities.supportsDocumentRender(capabilities, OutputFormat.PDF));
        assertTrue(ArchDoxAgentCapabilities.supportsDocumentRender(capabilities, OutputFormat.DOCX_AND_PDF));
        assertTrue(ArchDoxAgentCapabilities.supportsDocumentRender(capabilities, OutputFormat.HTML_AND_PDF));
    }

    @Test
    void hwpRequiresExplicitCapability() {
        var capabilities = Map.<String, Object>of(
                "documentGeneration", true,
                "outputFormats", List.of("DOCX", "HTML"));

        assertFalse(ArchDoxAgentCapabilities.supportsDocumentRender(capabilities, OutputFormat.HWP));
    }
}
