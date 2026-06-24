package com.archdox.cloud.inspection.application;

import com.archdox.cloud.inspection.infra.InspectionReportRepository;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class InspectionReportNumberGenerator {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final InspectionReportRepository reportRepository;

    public InspectionReportNumberGenerator(InspectionReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    public String nextReportNo(Long officeId, Long projectId, String reportType, OffsetDateTime now) {
        var prefix = reportTypePrefix(reportType) + "-" + DATE_FORMAT.format(now.toLocalDate()) + "-";
        var reportNos = reportRepository.findReportNosByPrefix(
                officeId,
                projectId,
                prefix);
        var nextSequence = reportNos.stream()
                .mapToInt(reportNo -> sequence(reportNo, prefix))
                .max()
                .orElse(0) + 1;
        return prefix + "%03d".formatted(nextSequence);
    }

    private String reportTypePrefix(String reportType) {
        if (reportType == null) {
            return "RPT";
        }
        return switch (reportType.trim().toUpperCase(Locale.ROOT)) {
            case "CONSTRUCTION_DAILY_SUPERVISION_LOG" -> "CSDL";
            case "CONSTRUCTION_SUPERVISION_CHECKLIST" -> "CSCL";
            case "CONSTRUCTION_SUPERVISION_REPORT" -> "CSR";
            default -> "RPT";
        };
    }

    private int sequence(String reportNo, String prefix) {
        if (reportNo == null || !reportNo.startsWith(prefix)) {
            return 0;
        }
        var suffix = reportNo.substring(prefix.length());
        if (suffix.isBlank() || !suffix.chars().allMatch(Character::isDigit)) {
            return 0;
        }
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
