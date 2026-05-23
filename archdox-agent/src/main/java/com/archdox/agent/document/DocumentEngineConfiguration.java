package com.archdox.agent.document;

import com.archdox.document.DocxTemplateDocumentEngine;
import com.archdox.document.DocumentArtifactExportService;
import com.archdox.document.DocumentArtifactExporter;
import com.archdox.document.DocumentEngine;
import com.archdox.document.LibreOfficeDocumentArtifactExporter;
import com.archdox.document.LibreOfficePdfExportOptions;
import com.archdox.document.PhotoContentResolver;
import com.archdox.document.SimpleDocumentEngine;
import java.util.ArrayList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentEngineConfiguration {
    @Bean
    DocumentArtifactExportService documentArtifactExportService(DocumentExportProperties exportProperties) {
        var exporters = new ArrayList<DocumentArtifactExporter>();
        var libreOffice = exportProperties.getLibreOffice();
        if (libreOffice.isEnabled()) {
            exporters.add(new LibreOfficeDocumentArtifactExporter(new LibreOfficePdfExportOptions(
                    libreOffice.getExecutablePath(),
                    libreOffice.getTimeoutMs())));
        }
        return new DocumentArtifactExportService(exporters);
    }

    @Bean
    DocumentEngine documentEngine(
            AgentTemplateContentResolver templateContentResolver,
            PhotoContentResolver photoContentResolver,
            DocumentArtifactExportService exportService
    ) {
        return new DocxTemplateDocumentEngine(
                templateContentResolver,
                photoContentResolver,
                new SimpleDocumentEngine(exportService),
                exportService);
    }
}
