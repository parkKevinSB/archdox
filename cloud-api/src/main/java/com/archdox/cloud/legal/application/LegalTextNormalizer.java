package com.archdox.cloud.legal.application;

import org.springframework.stereotype.Component;

@Component
public class LegalTextNormalizer {
    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f ]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
