package com.archdox.cloud.engine.usage.api;

import com.archdox.cloud.engine.usage.application.EngineApiUsageReadService;
import com.archdox.cloud.engine.usage.dto.EngineApiUsageEventResponse;
import com.archdox.cloud.engine.usage.dto.EngineApiUsageSummaryResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin/engine/usage")
public class PlatformEngineApiUsageController {
    private final EngineApiUsageReadService service;

    public PlatformEngineApiUsageController(EngineApiUsageReadService service) {
        this.service = service;
    }

    @GetMapping("/events")
    public List<EngineApiUsageEventResponse> events(
            Authentication authentication,
            @RequestParam(required = false) Long apiKeyId,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) String capability,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String reviewSessionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) Integer limit
    ) {
        return service.events(
                principal(authentication),
                apiKeyId,
                officeId,
                capability,
                operation,
                reviewSessionId,
                from,
                to,
                limit);
    }

    @GetMapping("/summary")
    public EngineApiUsageSummaryResponse summary(
            Authentication authentication,
            @RequestParam(required = false) Long apiKeyId,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) String capability,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return service.summary(
                principal(authentication),
                apiKeyId,
                officeId,
                capability,
                operation,
                from,
                to);
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
