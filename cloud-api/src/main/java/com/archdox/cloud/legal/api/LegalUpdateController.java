package com.archdox.cloud.legal.api;

import com.archdox.cloud.legal.application.LegalUpdateReadService;
import com.archdox.cloud.legal.dto.LegalChangeDigestResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/legal-updates")
public class LegalUpdateController {
    private final LegalUpdateReadService service;

    public LegalUpdateController(LegalUpdateReadService service) {
        this.service = service;
    }

    @GetMapping
    public List<LegalChangeDigestResponse> recent(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Integer limit
    ) {
        return service.recent(days, limit);
    }

    @GetMapping("/{id}")
    public LegalChangeDigestResponse detail(@PathVariable Long id) {
        return service.detail(id);
    }
}
