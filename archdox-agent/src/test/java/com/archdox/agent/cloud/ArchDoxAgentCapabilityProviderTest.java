package com.archdox.agent.cloud;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.archdox.agent.document.DocumentExportProperties;
import com.archdox.agent.document.LibreOfficeRuntimeAvailability;
import com.archdox.document.LibreOfficeCommandResult;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArchDoxAgentCapabilityProviderTest {
    @Test
    void advertisesPdfFormatsOnlyWhenLibreOfficeExportIsEnabledAndAvailable() {
        var properties = new DocumentExportProperties();
        var provider = new ArchDoxAgentCapabilityProvider(availableLibreOffice(properties));
        var disabled = provider.capabilities();

        assertFalse(outputFormats(disabled).contains("PDF"));
        assertFalse((Boolean) disabled.get("pdfExport"));

        properties.getLibreOffice().setEnabled(true);
        var enabled = provider.capabilities();

        assertTrue(outputFormats(enabled).contains("PDF"));
        assertTrue(outputFormats(enabled).contains("DOCX_AND_PDF"));
        assertTrue((Boolean) enabled.get("pdfExport"));
    }

    @Test
    void doesNotAdvertisePdfFormatsWhenLibreOfficeCannotRun() {
        var properties = new DocumentExportProperties();
        properties.getLibreOffice().setEnabled(true);

        var capabilities = new ArchDoxAgentCapabilityProvider(unavailableLibreOffice(properties)).capabilities();

        assertFalse(outputFormats(capabilities).contains("PDF"));
        assertFalse((Boolean) capabilities.get("pdfExport"));
    }

    private LibreOfficeRuntimeAvailability availableLibreOffice(DocumentExportProperties properties) {
        return new LibreOfficeRuntimeAvailability(
                properties,
                (command, timeout) -> new LibreOfficeCommandResult(0, "LibreOffice 24.8", false));
    }

    private LibreOfficeRuntimeAvailability unavailableLibreOffice(DocumentExportProperties properties) {
        return new LibreOfficeRuntimeAvailability(
                properties,
                (command, timeout) -> {
                    throw new IOException("soffice not found");
                });
    }

    @SuppressWarnings("unchecked")
    private List<String> outputFormats(java.util.Map<String, Object> capabilities) {
        return (List<String>) capabilities.get("outputFormats");
    }
}
