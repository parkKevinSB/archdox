package com.archdox.cloud.reportai.application;

import com.archdox.cloud.inspection.infra.InspectionReportStepRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ReportPreflightFieldValueResolver {
    private static final Pattern REMARKS_DIRECT_FIELD = Pattern.compile(
            ".*REMARKS(?:\\.payload)?\\.(issueAndAction|nextAction)$");

    private final InspectionReportStepRepository stepRepository;

    public ReportPreflightFieldValueResolver(InspectionReportStepRepository stepRepository) {
        this.stepRepository = stepRepository;
    }

    public Optional<String> resolve(Long reportId, String location) {
        var normalized = location == null ? "" : location.trim();
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        var remarksMatcher = REMARKS_DIRECT_FIELD.matcher(normalized);
        if (remarksMatcher.matches()) {
            return resolveRemarks(reportId, remarksMatcher.group(1));
        }
        return Optional.empty();
    }

    public Optional<String> resolveHash(Long reportId, String location) {
        return resolve(reportId, location)
                .map(ReportPreflightFieldValueResolver::hashText);
    }

    public static String hashText(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedText(value).getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }

    private Optional<String> resolveRemarks(Long reportId, String fieldName) {
        return stepRepository.findByReportIdAndStepCode(reportId, "REMARKS")
                .map(step -> text(step.payloadJson() == null ? null : step.payloadJson().get(fieldName)))
                .filter(value -> !value.isBlank());
    }

    private static String normalizedText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
