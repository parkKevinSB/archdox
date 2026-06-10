package com.archdox.cloud.documentai.api;

import com.archdox.cloud.documentai.application.DocumentNarrativePolishService;
import com.archdox.cloud.documentai.dto.DocumentNarrativeApplyResponse;
import com.archdox.cloud.documentai.dto.DocumentNarrativePolishRequest;
import com.archdox.cloud.documentai.dto.DocumentNarrativePolishResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DocumentNarrativePolishController {
    private final DocumentNarrativePolishService service;

    public DocumentNarrativePolishController(DocumentNarrativePolishService service) {
        this.service = service;
    }

    @PostMapping("/inspection-reports/{reportId}/document-narrative-polish")
    public DocumentNarrativePolishResponse polish(
            @PathVariable Long reportId,
            @RequestBody DocumentNarrativePolishRequest request,
            Authentication authentication
    ) {
        return service.polish(reportId, request, (UserPrincipal) authentication.getPrincipal());
    }

    @PostMapping("/inspection-reports/{reportId}/document-narrative-polish/apply")
    public DocumentNarrativeApplyResponse applyToReport(
            @PathVariable Long reportId,
            @RequestBody DocumentNarrativePolishRequest request,
            Authentication authentication
    ) {
        return service.applyToReport(reportId, request, (UserPrincipal) authentication.getPrincipal());
    }
}
