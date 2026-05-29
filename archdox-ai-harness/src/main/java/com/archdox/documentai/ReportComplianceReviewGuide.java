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
            "CONSTRUCTION_SUPERVISION_DAILY_LOG",
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
                    "Flag language that makes a legal conclusion too strongly without supporting facts."),
            "DEMOLITION_SAFETY_CHECK",
            List.of(
                    "For demolition safety checks, verify that demolition target, hazardous factors, worker/public safety, temporary structures, and waste or asbestos concerns are addressed.",
                    "Flag checklist answers that say safe/appropriate while notes or photos imply risk.",
                    "Flag missing corrective-action evidence for unsafe items.",
                    "Check whether photo evidence is enough to understand the safety condition."),
            "BUILDING_SAFETY_INSPECTION",
            List.of(
                    "For building safety inspection, verify that inspection target, location, defect condition, risk grade or opinion, and follow-up recommendations are coherent.",
                    "Flag structural/safety risk statements without location, severity, or evidence.",
                    "Flag contradictions between acceptable checklist results and defect notes.",
                    "Check whether the report can be understood by an owner, public agency, or reviewer without extra context."),
            "ASBESTOS_SUPERVISION",
            List.of(
                    "For asbestos supervision, verify that containment, protective equipment, air measurement, waste handling, and decontamination evidence are addressed when relevant.",
                    "Flag missing evidence for high-risk asbestos handling steps.",
                    "Flag vague statements such as checked/ok without measurement, condition, or photo context.",
                    "Do not fabricate measurement values; only flag missing or inconsistent values."));

    private ReportComplianceReviewGuide() {
    }

    static List<String> forReportType(String reportType) {
        if (reportType == null || reportType.isBlank()) {
            return DEFAULT_GUIDE;
        }
        return GUIDE_BY_REPORT_TYPE.getOrDefault(reportType.trim().toUpperCase(Locale.ROOT), DEFAULT_GUIDE);
    }
}
