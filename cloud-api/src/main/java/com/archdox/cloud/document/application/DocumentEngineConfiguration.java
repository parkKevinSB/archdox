package com.archdox.cloud.document.application;

import com.archdox.cloud.document.infra.DocumentLocalObjectStore;
import com.archdox.cloud.photo.infra.PhotoLocalObjectStore;
import com.archdox.document.DocxTemplateDocumentEngine;
import com.archdox.document.DocumentEngine;
import com.archdox.document.ResolvedPhotoContent;
import com.archdox.document.SimpleDocumentEngine;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentEngineConfiguration {
    @Bean
    DocumentEngine documentEngine(DocumentLocalObjectStore objectStore, PhotoLocalObjectStore photoObjectStore) {
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
        }, photo -> {
            if (photo.storageRef() == null || photo.storageRef().isBlank() || !photoObjectStore.exists(photo.storageRef())) {
                return Optional.empty();
            }
            try (var input = photoObjectStore.open(photo.storageRef())) {
                return Optional.of(new ResolvedPhotoContent(input.readAllBytes(), photo.mimeType()));
            }
        }, new SimpleDocumentEngine());
    }
}
