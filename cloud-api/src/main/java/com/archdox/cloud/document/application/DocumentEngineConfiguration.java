package com.archdox.cloud.document.application;

import com.archdox.cloud.document.infra.DocumentLocalObjectStore;
import com.archdox.document.DocxTemplateDocumentEngine;
import com.archdox.document.DocumentEngine;
import com.archdox.document.SimpleDocumentEngine;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentEngineConfiguration {
    @Bean
    DocumentEngine documentEngine(DocumentLocalObjectStore objectStore) {
        return new DocxTemplateDocumentEngine(template -> {
            if (template.storageRef() == null || template.storageRef().isBlank()) {
                return Optional.empty();
            }
            if (!objectStore.exists(template.storageRef())) {
                return Optional.empty();
            }
            try (var input = objectStore.open(template.storageRef())) {
                return Optional.of(input.readAllBytes());
            }
        }, new SimpleDocumentEngine());
    }
}
