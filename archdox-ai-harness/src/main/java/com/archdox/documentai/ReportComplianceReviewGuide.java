package com.archdox.documentai;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ReportComplianceReviewGuide {
    private static final List<String> DEFAULT_GUIDE = List.of(
            "Confirm that required dates, project/site identifiers, inspector/supervisor names, and target information are present.",
            "Check whether checklist answers are supported by narrative notes or photos when the answer implies a risk, defect, or corrective action.",
            "Flag vague legal or safety language that would be hard to defend in an audit, dispute, or public-agency review.",
            "Do not invent statutory article numbers. If a law reference is not present in the input, describe the compliance risk generically.");

    private static final Map<String, List<String>> GUIDE_BY_REPORT_TYPE = Map.of(
            "CONSTRUCTION_DAILY_SUPERVISION_LOG",
            List.of(
                    "For construction supervision daily logs, verify that supervised work, construction progress, safety/material checks, and special instructions are coherent.",
                    "If weather, work date, construction period, or supervision period conflict, flag it.",
                    "When defects or instructions are written, check that follow-up result or evidence is present.",
                    "Check whether the daily log would explain what was inspected and why the supervisor's judgment is reasonable."),
            "CONSTRUCTION_SUPERVISION_REPORT",
            List.of(
                    "For construction supervision reports, verify that project overview, supervision period, major inspected work, opinions, and attachments are coherent.",
                    "Flag missing relation-engineer or assistant-supervisor opinions when the report type appears to require them.",
                    "Check whether final conclusions are supported by checklist evidence and photos.",
                    "Flag language that makes a legal conclusion too strongly without supporting facts."));

    private ReportComplianceReviewGuide() {
    }

    static List<String> forReportType(String reportType) {
        if (reportType == null || reportType.isBlank()) {
            return DEFAULT_GUIDE;
        }
        return GUIDE_BY_REPORT_TYPE.getOrDefault(reportType.trim().toUpperCase(Locale.ROOT), DEFAULT_GUIDE);
    }
}
