package com.archdox.cloud.documentai.dto;

import java.util.List;

public record DocumentNarrativeApplyResponse(
        int appliedCount,
        List<String> appliedPaths
) {
}
