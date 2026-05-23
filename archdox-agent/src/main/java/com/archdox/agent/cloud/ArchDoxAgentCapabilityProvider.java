package com.archdox.agent.cloud;

import com.archdox.agent.document.DocumentExportProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ArchDoxAgentCapabilityProvider {
    private final DocumentExportProperties exportProperties;

    public ArchDoxAgentCapabilityProvider(DocumentExportProperties exportProperties) {
        this.exportProperties = exportProperties;
    }

    public Map<String, Object> capabilities() {
        var outputFormats = new ArrayList<String>();
        outputFormats.add("DOCX");
        outputFormats.add("HTML");
        if (exportProperties.getLibreOffice().isEnabled()) {
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
        capabilities.put("pdfExport", exportProperties.getLibreOffice().isEnabled());
        capabilities.put("outputFormats", outputFormats);
        capabilities.put("converters", Map.of(
                "libreOffice", exportProperties.getLibreOffice().isEnabled()));
        return capabilities;
    }
}
