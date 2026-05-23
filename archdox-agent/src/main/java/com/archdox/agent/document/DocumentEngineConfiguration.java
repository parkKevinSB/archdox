package com.archdox.agent.document;

import com.archdox.document.DocxTemplateDocumentEngine;
import com.archdox.document.DocumentEngine;
import com.archdox.document.PhotoContentResolver;
import com.archdox.document.SimpleDocumentEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentEngineConfiguration {
    @Bean
    DocumentEngine documentEngine(
            AgentTemplateContentResolver templateContentResolver,
            PhotoContentResolver photoContentResolver
    ) {
        return new DocxTemplateDocumentEngine(templateContentResolver, photoContentResolver, new SimpleDocumentEngine());
    }
}
