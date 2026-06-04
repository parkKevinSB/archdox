package com.archdox.cloud.engine.context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ArchDoxContextNormalizationService {
    private static final Set<String> REQUIRED_FIELDS = Set.of("buildingUse", "structureType", "workType");

    public ArchDoxNormalizedContext normalize(List<ArchDoxContextFact> facts) {
        var values = new LinkedHashMap<String, ArchDoxCanonicalContextValue>();
        var ambiguities = new ArrayList<ArchDoxContextAmbiguity>();

        for (var fact : facts == null ? List.<ArchDoxContextFact>of() : facts) {
            if (fact.fieldName().isBlank() || fact.rawValue().isBlank()) {
                continue;
            }

            var canonicalValue = canonicalValue(fact.fieldName(), fact.rawValue());
            var normalized = new ArchDoxCanonicalContextValue(
                    fact.fieldName(),
                    canonicalValue,
                    fact.rawValue(),
                    fact.confidence());
            values.merge(fact.fieldName(), normalized, this::higherConfidence);
            ambiguityFor(fact).ifPresent(ambiguities::add);
        }

        var missingQuestions = REQUIRED_FIELDS.stream()
                .filter(field -> !values.containsKey(field))
                .map(this::missingQuestion)
                .toList();

        return new ArchDoxNormalizedContext(values, missingQuestions, ambiguities);
    }

    private ArchDoxCanonicalContextValue higherConfidence(
            ArchDoxCanonicalContextValue current,
            ArchDoxCanonicalContextValue next
    ) {
        return next.confidence() > current.confidence() ? next : current;
    }

    private String canonicalValue(String fieldName, String rawValue) {
        var normalized = rawValue.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
        return switch (fieldName) {
            case "buildingUse" -> canonicalBuildingUse(normalized, rawValue);
            case "structureType" -> canonicalStructureType(normalized, rawValue);
            case "workType" -> canonicalWorkType(normalized, rawValue);
            default -> rawValue.trim();
        };
    }

    private String canonicalBuildingUse(String normalized, String rawValue) {
        if (normalized.contains("neighborhood") && normalized.contains("business")) {
            return "MULTI_USE";
        }
        if (normalized.contains("neighborhood") || rawValue.contains("근생") || rawValue.contains("근린생활")) {
            return "NEIGHBORHOOD_LIVING_FACILITY";
        }
        if (normalized.contains("business") || rawValue.contains("업무")) {
            return "BUSINESS_FACILITY";
        }
        return rawValue.trim();
    }

    private String canonicalStructureType(String normalized, String rawValue) {
        if (normalized.equals("rc")
                || normalized.contains("reinforced concrete")
                || rawValue.contains("철근콘크리트")) {
            return "REINFORCED_CONCRETE";
        }
        if (normalized.equals("s") || normalized.contains("steel")) {
            return "STEEL";
        }
        return rawValue.trim();
    }

    private String canonicalWorkType(String normalized, String rawValue) {
        if ((normalized.contains("foundation") && normalized.contains("concrete"))
                || rawValue.contains("기초 콘크리트")) {
            return "FOUNDATION_CONCRETE_PLACEMENT";
        }
        if (normalized.contains("slab") && normalized.contains("concrete")) {
            return "SLAB_CONCRETE_PLACEMENT";
        }
        if (normalized.contains("wall") && normalized.contains("concrete")) {
            return "WALL_CONCRETE_PLACEMENT";
        }
        return rawValue.trim();
    }

    private java.util.Optional<ArchDoxContextAmbiguity> ambiguityFor(ArchDoxContextFact fact) {
        var normalized = fact.rawValue().toLowerCase(Locale.ROOT);
        if ("buildingUse".equals(fact.fieldName())
                && ((normalized.contains("neighborhood") && normalized.contains("business"))
                || (fact.rawValue().contains("근린생활") && fact.rawValue().contains("업무")))) {
            return java.util.Optional.of(new ArchDoxContextAmbiguity(
                    fact.fieldName(),
                    fact.rawValue(),
                    List.of("NEIGHBORHOOD_LIVING_FACILITY", "BUSINESS_FACILITY"),
                    "Confirm whether both building-use categories should be reviewed."));
        }
        if ("workType".equals(fact.fieldName())
                && (normalized.equals("concrete placement") || fact.rawValue().equals("타설"))) {
            return java.util.Optional.of(new ArchDoxContextAmbiguity(
                    fact.fieldName(),
                    fact.rawValue(),
                    List.of(
                            "FOUNDATION_CONCRETE_PLACEMENT",
                            "SLAB_CONCRETE_PLACEMENT",
                            "WALL_CONCRETE_PLACEMENT"),
                    "Confirm which concrete placement location applies."));
        }
        return java.util.Optional.empty();
    }

    private ArchDoxMissingContextQuestion missingQuestion(String fieldName) {
        return switch (fieldName) {
            case "buildingUse" -> new ArchDoxMissingContextQuestion(
                    fieldName,
                    "What is the building use category?",
                    true);
            case "structureType" -> new ArchDoxMissingContextQuestion(
                    fieldName,
                    "What is the primary structure type?",
                    true);
            case "workType" -> new ArchDoxMissingContextQuestion(
                    fieldName,
                    "What construction or inspection work type should be reviewed?",
                    true);
            default -> new ArchDoxMissingContextQuestion(fieldName, "Please provide this context.", true);
        };
    }
}
