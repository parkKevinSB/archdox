package com.archdox.cloud.platformops.application;

import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.platformadmin.application.PlatformAdminService;
import com.archdox.cloud.platformops.domain.PlatformOpsControlProfile;
import com.archdox.cloud.platformops.domain.PlatformOpsControlProfileScope;
import com.archdox.cloud.platformops.domain.PlatformOpsControlProfileStatus;
import com.archdox.cloud.platformops.domain.PlatformOpsControlSignalKind;
import com.archdox.cloud.platformops.domain.PlatformOpsFindingSeverity;
import com.archdox.cloud.platformops.dto.CreatePlatformOpsControlProfileRequest;
import com.archdox.cloud.platformops.dto.PlatformOpsControlProfileResponse;
import com.archdox.cloud.platformops.dto.UpdatePlatformOpsControlProfileRequest;
import com.archdox.cloud.platformops.infra.PlatformOpsControlProfileRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformOpsControlProfileService {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 300;

    private final PlatformAdminService platformAdminService;
    private final PlatformOpsControlProfileRepository repository;

    public PlatformOpsControlProfileService(
            PlatformAdminService platformAdminService,
            PlatformOpsControlProfileRepository repository
    ) {
        this.platformAdminService = platformAdminService;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PlatformOpsControlProfileResponse> profiles(
            UserPrincipal principal,
            PlatformOpsControlProfileStatus status,
            Integer limit
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        return repository.search(status, PageRequest.of(0, normalizeLimit(limit)))
                .stream()
                .map(PlatformOpsControlProfileResponse::from)
                .toList();
    }

    @Transactional
    public PlatformOpsControlProfileResponse create(
            UserPrincipal principal,
            CreatePlatformOpsControlProfileRequest request
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        var signalKind = enumValue(request.signalKind(), PlatformOpsControlSignalKind.class, PlatformOpsControlSignalKind.I_LIKE, "signalKind");
        var scopeType = enumValue(request.scopeType(), PlatformOpsControlProfileScope.class, PlatformOpsControlProfileScope.GLOBAL, "scopeType");
        var modelId = normalizeModelId(request.modelId(), scopeType);
        var signalText = requiredText(request.signalText(), "signalText", 2, 2_000);
        var signalKey = signalKey(signalKind, scopeType, modelId, signalText);
        var severity = enumValue(request.severity(), PlatformOpsFindingSeverity.class, PlatformOpsFindingSeverity.WARN, "severity");
        var iWeight = normalizeWeight(request.iWeight());
        var now = OffsetDateTime.now();
        var profile = repository.findSignal(signalKind, scopeType, modelId, signalKey)
                .map(existing -> {
                    existing.observe(
                            severity,
                            iWeight,
                            request.sourceDailyReportId(),
                            request.notes(),
                            principal.userId(),
                            now);
                    return existing;
                })
                .orElseGet(() -> new PlatformOpsControlProfile(
                        signalKind,
                        scopeType,
                        modelId,
                        signalKey,
                        signalText,
                        severity,
                        iWeight,
                        request.sourceDailyReportId(),
                        request.notes(),
                        principal.userId(),
                        now));
        return PlatformOpsControlProfileResponse.from(repository.save(profile));
    }

    @Transactional
    public PlatformOpsControlProfileResponse update(
            UserPrincipal principal,
            Long profileId,
            UpdatePlatformOpsControlProfileRequest request
    ) {
        platformAdminService.requirePlatformAdmin(principal);
        var profile = repository.findById(profileId)
                .orElseThrow(() -> new NotFoundException("Platform ops control profile not found"));
        profile.update(
                enumValue(request.status(), PlatformOpsControlProfileStatus.class, null, "status"),
                enumValue(request.severity(), PlatformOpsFindingSeverity.class, null, "severity"),
                request.iWeight() == null ? null : normalizeWeight(request.iWeight()),
                request.notes(),
                principal.userId(),
                OffsetDateTime.now());
        return PlatformOpsControlProfileResponse.from(repository.save(profile));
    }

    private int normalizeLimit(Integer limit) {
        return Math.max(1, Math.min(limit == null ? DEFAULT_LIMIT : limit, MAX_LIMIT));
    }

    private String normalizeModelId(String value, PlatformOpsControlProfileScope scopeType) {
        var normalized = blankToNull(value);
        if (scopeType == PlatformOpsControlProfileScope.MODEL && normalized == null) {
            throw new BadRequestException("modelId is required for MODEL scoped control profile.");
        }
        return normalized;
    }

    private String requiredText(String value, String field, int minLength, int maxLength) {
        var normalized = blankToNull(value);
        if (normalized == null || normalized.length() < minLength) {
            throw new BadRequestException(field + " is required.");
        }
        if (normalized.length() > maxLength) {
            throw new BadRequestException(field + " is too long.");
        }
        return normalized;
    }

    private BigDecimal normalizeWeight(BigDecimal value) {
        var normalized = value == null ? BigDecimal.ONE : value;
        if (normalized.compareTo(BigDecimal.ZERO) < 0 || normalized.compareTo(new BigDecimal("100")) > 0) {
            throw new BadRequestException("iWeight must be between 0 and 100.");
        }
        return normalized;
    }

    private <E extends Enum<E>> E enumValue(String value, Class<E> enumType, E fallback, String field) {
        var normalized = blankToNull(value);
        if (normalized == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumType, normalized.toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BadRequestException(field + " is invalid.");
        }
    }

    private String signalKey(
            PlatformOpsControlSignalKind signalKind,
            PlatformOpsControlProfileScope scopeType,
            String modelId,
            String signalText
    ) {
        var source = signalKind.name()
                + "|"
                + scopeType.name()
                + "|"
                + (modelId == null ? "" : modelId.trim().toLowerCase(Locale.ROOT))
                + "|"
                + signalText.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to create control profile signal key", ex);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
