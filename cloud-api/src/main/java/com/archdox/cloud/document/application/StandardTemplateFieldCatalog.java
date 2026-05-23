package com.archdox.cloud.document.application;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class StandardTemplateFieldCatalog {
    private static final List<String> CONSTRUCTION_REPORT_TYPES = List.of(
            "CONSTRUCTION_SUPERVISION_REPORT",
            "SUPERVISION_REPORT");
    private static final List<String> DAILY_REPORT_TYPES = List.of(
            "DAILY_SUPERVISION",
            "CONSTRUCTION_DAILY_LOG");
    private static final List<String> DEMOLITION_SAFETY_REPORT_TYPES = List.of("DEMOLITION_SAFETY_CHECK");
    private static final List<String> DEMOLITION_DAILY_REPORT_TYPES = List.of(
            "DEMOLITION_DAILY_SUPERVISION",
            "DEMOLITION_DAILY_LOG");
    private static final List<String> DEMOLITION_COMPLETION_REPORT_TYPES = List.of("DEMOLITION_COMPLETION_REPORT");
    private static final List<String> ALL_DEMOLITION_REPORT_TYPES = List.of(
            "DEMOLITION_SAFETY_CHECK",
            "DEMOLITION_DAILY_SUPERVISION",
            "DEMOLITION_DAILY_LOG",
            "DEMOLITION_COMPLETION_REPORT");

    private static final List<TemplateFieldDefinition> FIELDS = List.of(
            field("documentTitle", "Document title", "report", "report.title, report.reportType", "Daily supervision log", "Resolved title shown at the top of a generated form."),
            field("reportTitle", "Report title", "report", "report.title", "North site daily inspection", "User-facing report title."),
            field("reportNo", "Report number", "report", "report.reportNo", "R-2026-001", "Report or serial number."),
            field("serialNo", "Serial number", "report", "report.reportNo", "R-2026-001", "Alias for public forms that call the report number a serial number."),
            field("reportType", "Report type", "report", "report.reportType", "DAILY_SUPERVISION", "Normalized ArchDox report type."),

            field("projectName", "Project name", "project-site", "project.name", "Document Tower", "Project container name."),
            field("constructionName", "Construction name", "project-site", "project.name", "Document Tower", "Public-form alias for project name."),
            field("constructionProjectName", "Construction project name", "project-site", "project.name", "Document Tower", "Long-form construction project name alias."),
            field("workName", "Work name", "project-site", "project.name", "Document Tower", "Generic work name alias used by completion forms."),
            field("projectAddress", "Project address", "project-site", "project.address", "Seoul", "Project-level address."),
            field("siteName", "Site name", "project-site", "site.name, project.name", "North Site", "Selected site name, falling back to project name."),
            field("siteCode", "Site code", "project-site", "site.siteCode", "N-1", "Office-defined site code."),
            field("siteAddress", "Site address", "project-site", "site.address, project.address", "Seoul", "Selected site address, falling back to project address."),
            field("siteType", "Site type", "project-site", "site.siteType", "NEW_BUILDING", "Configured site type."),
            field("buildingType", "Building type", "project-site", "project.buildingType", "Office", "Project building type."),
            field("lotNumber", "Lot number", "project-site", "steps.BASIC_INFO.payload.lotNumber", "123-4", "Lot number captured in basic information."),

            field("permitNumber", "Permit number", "permit-period", "steps.BASIC_INFO.payload.permitNumber", "2026-ARCH-01", "Permit number for report-style forms.", CONSTRUCTION_REPORT_TYPES),
            field("permitDate", "Permit date", "permit-period", "steps.BASIC_INFO.payload.permitDate", "2026-05-01", "Permit date for report-style forms.", CONSTRUCTION_REPORT_TYPES),
            field("constructionStartDate", "Construction start date", "permit-period", "project.startDate, site.startDate", "2026-05-01", "Construction period start."),
            field("constructionEndDate", "Construction end date", "permit-period", "project.endDate, site.endDate", "2026-08-31", "Construction period end."),
            field("supervisionStartDate", "Supervision start date", "permit-period", "steps.BASIC_INFO.payload.supervisionStartDate, project.startDate", "2026-05-01", "Supervision period start.", CONSTRUCTION_REPORT_TYPES),
            field("supervisionEndDate", "Supervision end date", "permit-period", "steps.BASIC_INFO.payload.supervisionEndDate, project.endDate", "2026-08-31", "Supervision period end.", CONSTRUCTION_REPORT_TYPES),

            field("inspectionDate", "Inspection date", "date-weather", "steps.*.payload.inspectionDate", "2026-05-23", "Primary inspection or work date."),
            field("safetyInspectionDate", "Safety inspection date", "date-weather", "steps.*.payload.inspectionDate", "2026-05-23", "Alias for safety checklist forms.", DEMOLITION_SAFETY_REPORT_TYPES),
            field("inspectionYear", "Inspection year", "date-weather", "inspectionDate", "2026", "Year extracted from inspectionDate."),
            field("inspectionMonth", "Inspection month", "date-weather", "inspectionDate", "5", "Month extracted from inspectionDate."),
            field("inspectionDay", "Inspection day", "date-weather", "inspectionDate", "23", "Day extracted from inspectionDate."),
            field("inspectionDayOfWeek", "Inspection day of week", "date-weather", "inspectionDate", "Sat", "Korean weekday text extracted from inspectionDate."),
            field("weather", "Weather", "date-weather", "steps.BASIC_INFO.payload.weather, steps.DAILY_LOG.payload.weather", "Clear", "Daily log weather."),
            field("inspectionLocation", "Inspection location", "date-weather", "steps.*.payload.location, site.name, site.address", "Roof", "Inspection location or fallback site reference."),

            field("chiefSupervisorName", "Chief supervisor", "people", "steps.BASIC_INFO.payload.chiefSupervisorName", "Lee", "Chief supervisor name."),
            field("supervisorName", "Supervisor", "people", "steps.BASIC_INFO.payload.supervisorName", "Lee", "Supervisor or inspector in charge."),
            field("inspectorName", "Inspector", "people", "steps.BASIC_INFO.payload.inspectorName", "Kim", "Inspector who wrote the report."),
            field("architectAssistantName", "Architect assistant", "people", "steps.BASIC_INFO.payload.architectAssistantName", "Park", "Assistant architect or site assistant.", DAILY_REPORT_TYPES),
            field("assistantSupervisorName", "Assistant supervisor", "people", "steps.BASIC_INFO.payload.assistantSupervisorName", "Park", "Assistant supervisor alias.", DAILY_REPORT_TYPES),
            field("demolitionWorkerName", "Demolition worker", "people", "steps.DEMOLITION_SAFETY_CHECK.payload.demolitionWorkerName", "Park", "Worker or contractor representative for demolition forms.", ALL_DEMOLITION_REPORT_TYPES),

            field("constructionTrade", "Construction trade", "daily-content", "steps.DAILY_LOG.payload.constructionTrade", "Concrete", "Trade or work type for daily supervision.", DAILY_REPORT_TYPES),
            field("detailedProcess", "Detailed process", "daily-content", "steps.DAILY_LOG.payload.detailedProcess", "Slab", "Detailed work process.", DAILY_REPORT_TYPES),
            field("floor", "Floor", "daily-content", "steps.DAILY_LOG.payload.floor", "3F", "Floor or zone for the work item.", DAILY_REPORT_TYPES),
            field("workDescription", "Work description", "daily-content", "steps.*.payload.workDescription", "Rebar placement", "Work summary for daily logs.", List.of("DAILY_SUPERVISION", "CONSTRUCTION_DAILY_LOG", "DEMOLITION_DAILY_SUPERVISION", "DEMOLITION_DAILY_LOG")),
            field("supervisionItem", "Supervision item", "daily-content", "steps.DAILY_LOG.payload.supervisionItem", "Rebar spacing", "Supervision item or checklist item.", DAILY_REPORT_TYPES),
            field("supervisionFocus", "Supervision focus", "daily-content", "steps.*.payload.supervisionFocus", "Temporary support", "Focus point for demolition daily supervision.", DEMOLITION_DAILY_REPORT_TYPES),
            field("supervisionContent", "Supervision content", "daily-content", "steps.*.payload.supervisionContent", "Checked rebar", "Main supervision content.", List.of("DAILY_SUPERVISION", "CONSTRUCTION_DAILY_LOG", "DEMOLITION_DAILY_SUPERVISION", "DEMOLITION_DAILY_LOG")),
            field("specialNotes", "Special notes", "daily-content", "steps.*.payload.specialNotes", "No special issue", "Special notes section.", DAILY_REPORT_TYPES),
            field("issueAndAction", "Issue and action result", "daily-content", "steps.DAILY_LOG.payload.issueAndAction", "None", "Issue and treatment result for daily logs.", DAILY_REPORT_TYPES),
            field("correctiveAction", "Corrective action", "checklist-demolition", "steps.*.payload.correctiveAction", "Tighten supports", "Corrective action for issue or safety result."),
            field("checklistSummary", "Checklist summary", "checklist-demolition", "steps.CHECKLIST.payload.checklistSummary", "Checked", "Summary of checklist answers."),
            field("issueCount", "Issue count", "checklist-demolition", "steps.CHECKLIST.payload.issueCount", "0", "Number of checklist or inspection issues."),

            field("safetyCheckStage", "Safety check stage", "checklist-demolition", "steps.DEMOLITION_SAFETY_CHECK.payload.stage", "Roof demolition", "Demolition safety checklist stage.", DEMOLITION_SAFETY_REPORT_TYPES),
            field("demolitionWorkStage", "Demolition work stage", "checklist-demolition", "steps.*.payload.stage", "Roof demolition", "Demolition work stage alias.", ALL_DEMOLITION_REPORT_TYPES),
            field("inspectionCriteria", "Inspection criteria", "checklist-demolition", "steps.DEMOLITION_SAFETY_CHECK.payload.inspectionCriteria", "Jack support spacing", "Safety inspection criteria.", DEMOLITION_SAFETY_REPORT_TYPES),
            field("inspectionResult", "Inspection result", "checklist-demolition", "steps.DEMOLITION_SAFETY_CHECK.payload.inspectionResult", "Pass", "Safety inspection result.", DEMOLITION_SAFETY_REPORT_TYPES),
            field("safetyChecklistItems", "Safety checklist items", "checklist-demolition", "checklistAnswers", "Support spacing / Pass / OK", "Compact text list of checklist answers.", DEMOLITION_SAFETY_REPORT_TYPES)
    );

    private static final List<TemplateFormPreset> PRESETS = List.of(
            preset(
                    "KOREAN_CONSTRUCTION_SUPERVISION_REPORT_APPENDIX_1",
                    "Korean construction supervision report appendix 1",
                    "Field set for construction supervision summary reports.",
                    CONSTRUCTION_REPORT_TYPES,
                    List.of("documentTitle", "permitNumber", "permitDate", "siteAddress", "lotNumber", "constructionName", "supervisionStartDate", "supervisionEndDate", "chiefSupervisorName", "supervisorName", "specialNotes"),
                    List.of("CHECKLIST_TABLE", "PHOTO_TABLE")),
            preset(
                    "KOREAN_CONSTRUCTION_DAILY_SUPERVISION_APPENDIX_2",
                    "Korean construction daily supervision appendix 2",
                    "Field set for daily construction supervision logs.",
                    DAILY_REPORT_TYPES,
                    List.of("documentTitle", "serialNo", "chiefSupervisorName", "architectAssistantName", "constructionName", "inspectionDate", "inspectionDayOfWeek", "weather", "constructionTrade", "detailedProcess", "floor", "supervisionItem", "supervisionContent", "specialNotes", "issueAndAction"),
                    List.of("CHECKLIST_TABLE", "PHOTO_TABLE")),
            preset(
                    "KOREAN_DEMOLITION_SAFETY_CHECK_APPENDIX_1",
                    "Korean demolition safety checklist appendix 1",
                    "Field set for demolition safety stage checks.",
                    DEMOLITION_SAFETY_REPORT_TYPES,
                    List.of("documentTitle", "safetyInspectionDate", "inspectionLocation", "supervisorName", "demolitionWorkerName", "safetyCheckStage", "inspectionCriteria", "inspectionResult", "correctiveAction", "safetyChecklistItems"),
                    List.of("CHECKLIST_TABLE", "PHOTO_TABLE")),
            preset(
                    "KOREAN_DEMOLITION_DAILY_SUPERVISION_APPENDIX_2",
                    "Korean demolition daily supervision appendix 2",
                    "Field set for demolition daily supervision logs.",
                    DEMOLITION_DAILY_REPORT_TYPES,
                    List.of("documentTitle", "supervisorName", "assistantSupervisorName", "constructionName", "inspectionDate", "inspectionDayOfWeek", "weather", "workDescription", "demolitionWorkStage", "supervisionFocus", "supervisionContent", "specialNotes", "issueAndAction"),
                    List.of("CHECKLIST_TABLE", "PHOTO_TABLE")),
            preset(
                    "KOREAN_DEMOLITION_COMPLETION_REPORT_APPENDIX_3",
                    "Korean demolition completion report appendix 3",
                    "Field set for demolition supervision completion reports.",
                    DEMOLITION_COMPLETION_REPORT_TYPES,
                    List.of("documentTitle", "workName", "siteAddress", "supervisorName", "demolitionWorkerName", "constructionStartDate", "constructionEndDate", "specialNotes", "checklistSummary"),
                    List.of("CHECKLIST_TABLE", "PHOTO_TABLE"))
    );

    public TemplateFieldCatalog catalog(String reportType) {
        var normalizedReportType = normalize(reportType);
        return new TemplateFieldCatalog(
                normalizedReportType,
                FIELDS.stream()
                        .filter(field -> supports(field.reportTypes(), normalizedReportType))
                        .toList(),
                PRESETS.stream()
                        .filter(preset -> supports(preset.reportTypes(), normalizedReportType))
                        .toList());
    }

    private boolean supports(List<String> reportTypes, String reportType) {
        return reportType == null || reportTypes.isEmpty() || reportTypes.contains(reportType);
    }

    private String normalize(String reportType) {
        if (reportType == null || reportType.isBlank()) {
            return null;
        }
        return reportType.trim().toUpperCase(Locale.ROOT);
    }

    private static TemplateFieldDefinition field(
            String key,
            String label,
            String category,
            String source,
            String example,
            String description
    ) {
        return field(key, label, category, source, example, description, List.of());
    }

    private static TemplateFieldDefinition field(
            String key,
            String label,
            String category,
            String source,
            String example,
            String description,
            List<String> reportTypes
    ) {
        return new TemplateFieldDefinition(key, label, category, source, example, description, List.copyOf(reportTypes));
    }

    private static TemplateFormPreset preset(
            String code,
            String title,
            String description,
            List<String> reportTypes,
            List<String> recommendedFields,
            List<String> layoutSections
    ) {
        return new TemplateFormPreset(
                code,
                title,
                description,
                List.copyOf(reportTypes),
                List.copyOf(recommendedFields),
                List.copyOf(layoutSections));
    }

    public record TemplateFieldCatalog(
            String reportType,
            List<TemplateFieldDefinition> fields,
            List<TemplateFormPreset> presets
    ) {
    }

    public record TemplateFieldDefinition(
            String key,
            String label,
            String category,
            String source,
            String example,
            String description,
            List<String> reportTypes
    ) {
    }

    public record TemplateFormPreset(
            String code,
            String title,
            String description,
            List<String> reportTypes,
            List<String> recommendedFields,
            List<String> layoutSections
    ) {
    }
}
