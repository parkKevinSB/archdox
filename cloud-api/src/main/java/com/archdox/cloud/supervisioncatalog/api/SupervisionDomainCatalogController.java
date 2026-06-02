package com.archdox.cloud.supervisioncatalog.api;

import com.archdox.cloud.supervisioncatalog.application.SupervisionDomainCatalogService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/supervision-domain-catalogs")
public class SupervisionDomainCatalogController {
    private final SupervisionDomainCatalogService service;

    public SupervisionDomainCatalogController(SupervisionDomainCatalogService service) {
        this.service = service;
    }

    @GetMapping("/{catalogCode}")
    public JsonNode get(@PathVariable String catalogCode) {
        return service.get(catalogCode);
    }
}
