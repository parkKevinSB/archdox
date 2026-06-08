package com.archdox.cloud.legal.application;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class LegalPublicSourceUrlFactory {
    public String publicSourceUrl(String sourceUrl, String actType, String actName) {
        var normalizedSourceUrl = text(sourceUrl);
        var target = queryParam(normalizedSourceUrl, "target");
        var sourceId = queryParam(normalizedSourceUrl, "ID");
        if ("admrul".equalsIgnoreCase(target) && !sourceId.isBlank()) {
            return "https://www.law.go.kr/LSW/admRulInfoP.do?admRulSeq="
                    + encode(sourceId)
                    + "&chrClsCd=010201";
        }
        var normalizedActName = text(actName);
        if (!normalizedActName.isBlank()) {
            var category = "ADMINISTRATIVE_RULE".equalsIgnoreCase(text(actType)) ? "행정규칙" : "법령";
            return "https://www.law.go.kr/" + encode(category) + "/" + encode(normalizedActName);
        }
        return "";
    }

    private String queryParam(String url, String name) {
        if (url.isBlank()) {
            return "";
        }
        try {
            var query = URI.create(url).getRawQuery();
            if (query == null || query.isBlank()) {
                return "";
            }
            for (var pair : query.split("&")) {
                var parts = pair.split("=", 2);
                if (parts.length == 2 && name.equalsIgnoreCase(parts[0])) {
                    return parts[1].trim();
                }
            }
        } catch (IllegalArgumentException ignored) {
            return "";
        }
        return "";
    }

    private String encode(String value) {
        return URLEncoder.encode(text(value), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
