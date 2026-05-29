package com.archdox.cloud.site.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.site.application.SiteService;
import com.archdox.cloud.site.dto.CreateSiteRequest;
import com.archdox.cloud.site.dto.SiteResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/sites")
public class SiteController {
    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    @GetMapping
    public List<SiteResponse> list(@PathVariable Long projectId) {
        return siteService.list(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SiteResponse create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateSiteRequest request,
            Authentication authentication
    ) {
        return siteService.create(projectId, request, (UserPrincipal) authentication.getPrincipal());
    }

    @GetMapping("/{siteId}")
    public SiteResponse get(@PathVariable Long projectId, @PathVariable Long siteId) {
        return siteService.get(projectId, siteId);
    }

    @PostMapping("/{siteId}/archive")
    public SiteResponse archive(
            @PathVariable Long projectId,
            @PathVariable Long siteId,
            Authentication authentication
    ) {
        return siteService.archive(projectId, siteId, (UserPrincipal) authentication.getPrincipal());
    }

    @DeleteMapping("/{siteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long projectId,
            @PathVariable Long siteId,
            Authentication authentication
    ) {
        siteService.delete(projectId, siteId, (UserPrincipal) authentication.getPrincipal());
    }
}
