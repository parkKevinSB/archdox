package com.archdox.cloud.engine.api;

import com.archdox.cloud.engine.application.EngineExternalLegalEvidenceService;
import com.archdox.cloud.engine.auth.application.EngineApiPrincipal;
import com.archdox.cloud.legal.dto.LegalLawArticleResponse;
import com.archdox.cloud.legal.dto.LegalLawSearchResponse;
import java.time.LocalDate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/engine/external/legal")
public class EngineExternalLegalEvidenceController {
    private final EngineExternalLegalEvidenceService service;

    public EngineExternalLegalEvidenceController(EngineExternalLegalEvidenceService service) {
        this.service = service;
    }

    @GetMapping("/search")
    public LegalLawSearchResponse search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String actCode,
            @RequestParam(required = false) String actName,
            @RequestParam(required = false) String articleNo,
            @RequestParam(required = false) LocalDate effectiveDate,
            @RequestParam(required = false) Integer limit,
            Authentication authentication
    ) {
        return service.search(
                apiPrincipal(authentication),
                query,
                actCode,
                actName,
                articleNo,
                effectiveDate,
                limit);
    }

    @GetMapping("/articles")
    public LegalLawArticleResponse article(
            @RequestParam(required = false) Long articleVersionId,
            @RequestParam(required = false) Long articleId,
            @RequestParam(required = false) String actCode,
            @RequestParam(required = false) String articleNo,
            @RequestParam(required = false) LocalDate effectiveDate,
            Authentication authentication
    ) {
        return service.getArticle(
                apiPrincipal(authentication),
                articleVersionId,
                articleId,
                actCode,
                articleNo,
                effectiveDate);
    }

    private EngineApiPrincipal apiPrincipal(Authentication authentication) {
        return (EngineApiPrincipal) authentication.getPrincipal();
    }
}
