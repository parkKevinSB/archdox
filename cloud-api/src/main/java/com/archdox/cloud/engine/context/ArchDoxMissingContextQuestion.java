package com.archdox.cloud.engine.context;

public record ArchDoxMissingContextQuestion(
        String fieldName,
        String question,
        boolean required
) {
    public ArchDoxMissingContextQuestion {
        fieldName = fieldName == null ? "" : fieldName.trim();
        question = question == null ? "" : question.trim();
    }
}
