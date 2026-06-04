package com.archdox.cloud.engine.context;

import java.util.List;
import java.util.Map;

public record ArchDoxNormalizedContext(
        Map<String, ArchDoxCanonicalContextValue> values,
        List<ArchDoxMissingContextQuestion> missingQuestions,
        List<ArchDoxContextAmbiguity> ambiguities
) {
    public ArchDoxNormalizedContext {
        values = values == null ? Map.of() : Map.copyOf(values);
        missingQuestions = missingQuestions == null ? List.of() : List.copyOf(missingQuestions);
        ambiguities = ambiguities == null ? List.of() : List.copyOf(ambiguities);
    }
}
