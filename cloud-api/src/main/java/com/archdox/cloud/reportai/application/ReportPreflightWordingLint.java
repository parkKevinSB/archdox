package com.archdox.cloud.reportai.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class ReportPreflightWordingLint {
    private static final Pattern PARTICLE_SEQUENCE = Pattern.compile(".*\\S+\\s+\uACFC\\s+\uB97C\\s+.*");

    private ReportPreflightWordingLint() {
    }

    static List<ReportPreflightFinding> dailySupervisionContent(
            String value,
            String location,
            int groupNo,
            int entryNo
    ) {
        var text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            return List.of();
        }
        var findings = new ArrayList<ReportPreflightFinding>();
        var typoReplacement = typoReplacement(text);
        if (!typoReplacement.equals(text)) {
            findings.add(finding(
                    "DAILY_LOG_WORDING_TYPO",
                    location,
                    "The supervision content contains an obvious typo.",
                    "knownTypo=true",
                    text,
                    groupNo,
                    entryNo,
                    Map.of(
                            "suggestion", "Fix the obvious typo and keep the sentence as final report prose.",
                            "replacement", typoReplacement,
                            "rule", "known-korean-typo")));
        }
        if (PARTICLE_SEQUENCE.matcher(text).matches()) {
            findings.add(finding(
                    "DAILY_LOG_WORDING_PARTICLE_SEQUENCE",
                    location,
                    "The supervision content has an incomplete Korean particle sequence.",
                    "particleSequence=\uACFC \uB97C",
                    text,
                    groupNo,
                    entryNo,
                    Map.of(
                            "suggestion", "Complete the sentence by adding the missing object or inspection target.",
                            "rule", "particle-sequence")));
        }
        return List.copyOf(findings);
    }

    private static ReportPreflightFinding finding(
            String code,
            String location,
            String message,
            String evidence,
            String fieldValue,
            int groupNo,
            int entryNo,
            Map<String, String> extraAttributes
    ) {
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("category", "WORDING");
        attributes.put("approvalRequired", "true");
        attributes.put("reportType", "CONSTRUCTION_DAILY_SUPERVISION_LOG");
        attributes.put("groupNo", String.valueOf(groupNo));
        attributes.put("entryNo", String.valueOf(entryNo));
        attributes.put("fieldValueHash", ReportPreflightFieldValueResolver.hashText(fieldValue));
        attributes.putAll(extraAttributes);
        return new ReportPreflightFinding(
                "DETERMINISTIC",
                code,
                "MEDIUM",
                location,
                message,
                evidence,
                attributes);
    }

    private static String typoReplacement(String text) {
        return text
                .replace("\uD655\uC774\uD588\uC501\uB2C8\uB2E4", "\uD655\uC778\uD588\uC2B5\uB2C8\uB2E4")
                .replace("\uD655\uC774\uD588\uC2B5\uB2C8\uB2E4", "\uD655\uC778\uD588\uC2B5\uB2C8\uB2E4")
                .replace("\uD655\uC778\uD588\uC501\uB2C8\uB2E4", "\uD655\uC778\uD588\uC2B5\uB2C8\uB2E4")
                .replace("\uD655\uC778\uD588\uC74D\uB2C8\uB2E4", "\uD655\uC778\uD588\uC2B5\uB2C8\uB2E4");
    }
}
