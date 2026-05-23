package com.archdox.cloud.documenttype.api;

import com.archdox.cloud.documenttype.application.DocumentTypeRegistryService;
import com.archdox.cloud.documenttype.dto.DocumentTypeResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/document-types")
public class DocumentTypeController {
    private final DocumentTypeRegistryService service;

    public DocumentTypeController(DocumentTypeRegistryService service) {
        this.service = service;
    }

    @GetMapping
    public List<DocumentTypeResponse> list() {
        return service.listVisible();
    }

    @GetMapping("/{code}")
    public DocumentTypeResponse get(@PathVariable String code) {
        return service.get(code);
    }
}
