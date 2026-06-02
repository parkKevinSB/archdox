package com.archdox.cloud.supervisioncatalog.application;

import com.archdox.cloud.global.api.NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class SupervisionDomainCatalogService {
    public static final String CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24 =
            "CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24";

    private static final Map<String, String> RESOURCE_BY_CODE = Map.of(
            CONSTRUCTION_SUPERVISION_CHECKLIST_2020_12_24,
            "domain-catalogs/construction-supervision-checklist-2020-12-24.json"
    );

    private final ObjectMapper objectMapper;

    public SupervisionDomainCatalogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode get(String catalogCode) {
        var normalized = normalize(catalogCode);
        var resourcePath = RESOURCE_BY_CODE.get(normalized);
        if (resourcePath == null) {
            throw new NotFoundException(
                    "SUPERVISION_CATALOG_NOT_FOUND",
                    "errors.supervisionCatalog.notFound",
                    "Supervision domain catalog not found");
        }
        try (var input = new ClassPathResource(resourcePath).getInputStream()) {
            return objectMapper.readTree(input);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read supervision domain catalog: " + normalized, ex);
        }
    }

    private String normalize(String catalogCode) {
        return catalogCode == null ? "" : catalogCode.trim().toUpperCase();
    }
}
