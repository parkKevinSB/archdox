package com.archdox.cloud.officestorage.api;

import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.officestorage.application.OfficeStorageProfileService;
import com.archdox.cloud.officestorage.dto.OfficeStorageConnectionTestResponse;
import com.archdox.cloud.officestorage.dto.OfficeStorageProfileResponse;
import com.archdox.cloud.officestorage.dto.SaveOfficeStorageProfileRequest;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/office-ops/storage-profiles")
public class OfficeStorageProfileController {
    private final OfficeStorageProfileService service;

    public OfficeStorageProfileController(OfficeStorageProfileService service) {
        this.service = service;
    }

    @GetMapping
    public List<OfficeStorageProfileResponse> list(Authentication authentication) {
        return service.list(principal(authentication));
    }

    @PostMapping
    public OfficeStorageProfileResponse save(
            Authentication authentication,
            @RequestBody SaveOfficeStorageProfileRequest request
    ) {
        return service.save(principal(authentication), request);
    }

    @PostMapping("/{profileId}/test")
    public OfficeStorageConnectionTestResponse test(
            Authentication authentication,
            @PathVariable Long profileId
    ) {
        return service.test(principal(authentication), profileId);
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
