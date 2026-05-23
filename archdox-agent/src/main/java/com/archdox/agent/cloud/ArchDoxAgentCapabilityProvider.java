package com.archdox.agent.cloud;

import com.archdox.agent.document.LibreOfficeRuntimeAvailability;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ArchDoxAgentCapabilityProvider {
    private final LibreOfficeRuntimeAvailability libreOfficeRuntimeAvailability;

    public ArchDoxAgentCapabilityProvider(LibreOfficeRuntimeAvailability libreOfficeRuntimeAvailability) {
        this.libreOfficeRuntimeAvailability = libreOfficeRuntimeAvailability;
    }

    public Map<String, Object> capabilities() {
        var libreOffice = libreOfficeRuntimeAvailability.probe();
        var pdfExportAvailable = libreOffice.pdfExportAvailable();

        var outputFormats = new ArrayList<String>();
        outputFormats.add("DOCX");
        outputFormats.add("HTML");
        if (pdfExportAvailable) {
            outputFormats.add("PDF");
            outputFormats.add("DOCX_AND_PDF");
            outputFormats.add("HTML_AND_PDF");
        }

        var capabilities = new LinkedHashMap<String, Object>();
        capabilities.put("nas", true);
        capabilities.put("photoPickup", true);
        capabilities.put("documentGeneration", true);
        capabilities.put("documentRender", true);
        capabilities.put("documentArtifactDelivery", true);
        capabilities.put("pdfExport", pdfExportAvailable);
        capabilities.put("outputFormats", outputFormats);
        capabilities.put("converters", Map.of(
                "libreOffice", pdfExportAvailable));
        capabilities.put("converterDetails", Map.of(
                "libreOffice", Map.of(
                        "enabled", libreOffice.enabled(),
                        "available", libreOffice.available(),
                        "executablePath", libreOffice.executablePath(),
                        "version", libreOffice.version(),
                        "message", libreOffice.message())));
        return capabilities;
    }
}
