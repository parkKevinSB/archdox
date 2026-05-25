package com.archdox.agent.cloud;

import com.archdox.agent.document.LibreOfficeRuntimeAvailability;
import com.archdox.agent.storage.AgentStorageKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ArchDoxAgentCapabilityProvider {
    private final LibreOfficeRuntimeAvailability libreOfficeRuntimeAvailability;
    private final ArchDoxAgentProperties properties;

    public ArchDoxAgentCapabilityProvider(
            LibreOfficeRuntimeAvailability libreOfficeRuntimeAvailability,
            ArchDoxAgentProperties properties
    ) {
        this.libreOfficeRuntimeAvailability = libreOfficeRuntimeAvailability;
        this.properties = properties;
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
        capabilities.put("nas", hasNasStorageProfile());
        capabilities.put("s3CompatibleStorage", hasS3CompatibleStorageProfile());
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

    private boolean hasNasStorageProfile() {
        return properties.originalStorageProfile().kind() == AgentStorageKind.NAS
                || properties.workingStorageProfile().kind() == AgentStorageKind.NAS
                || properties.artifactStorageProfile().kind() == AgentStorageKind.NAS
                || properties.templateStorageProfile().kind() == AgentStorageKind.NAS;
    }

    private boolean hasS3CompatibleStorageProfile() {
        return properties.originalStorageProfile().kind() == AgentStorageKind.S3_COMPATIBLE
                || properties.workingStorageProfile().kind() == AgentStorageKind.S3_COMPATIBLE
                || properties.artifactStorageProfile().kind() == AgentStorageKind.S3_COMPATIBLE
                || properties.templateStorageProfile().kind() == AgentStorageKind.S3_COMPATIBLE;
    }
}
