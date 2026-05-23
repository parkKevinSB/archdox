package com.archdox.document;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class DocxTemplateDocumentEngine implements DocumentEngine {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_.\\-\\[\\]]+)}");
    private static final Pattern WORD_TEXT_NODE_PATTERN = Pattern.compile("(<w:t(?:\\s+[^>]*)?>)(.*?)(</w:t>)",
            Pattern.DOTALL);

    private final TemplateContentResolver templateContentResolver;
    private final DocumentEngine fallback;

    public DocxTemplateDocumentEngine(TemplateContentResolver templateContentResolver, DocumentEngine fallback) {
        this.templateContentResolver = templateContentResolver;
        this.fallback = fallback;
    }

    @Override
    public DocumentGenerationResult generate(DocumentGenerationRequest request) {
        if (request.outputFormat() == OutputFormat.PDF || request.outputFormat() == OutputFormat.DOCX_AND_PDF) {
            return DocumentGenerationResult.failed(
                    request.jobId(),
                    "UNSUPPORTED_OUTPUT_FORMAT",
                    "PDF conversion is not implemented in the MVP document engine");
        }

        try {
            Optional<byte[]> templateContent = templateContentResolver.resolve(request.template());
            if (templateContent.isEmpty()) {
                return fallback.generate(request);
            }

            var fileName = "inspection-report-" + sanitizeFileName(request.reportId()) + ".docx";
            var storageRef = "documents/jobs/" + sanitizeFileName(request.jobId()) + "/" + fileName;
            var content = bindTemplate(templateContent.get(), buildBindings(request));
            var artifact = new GeneratedArtifact(
                    ArtifactType.DOCX,
                    fileName,
                    storageRef,
                    content.length,
                    sha256(content),
                    content);
            return DocumentGenerationResult.completed(request.jobId(), List.of(artifact));
        } catch (IOException ex) {
            return DocumentGenerationResult.failed(request.jobId(), "TEMPLATE_BINDING_FAILED", ex.getMessage());
        } catch (RuntimeException ex) {
            return DocumentGenerationResult.failed(request.jobId(), "DOCUMENT_RENDER_FAILED", ex.getMessage());
        }
    }

    private byte[] bindTemplate(byte[] templateContent, Map<String, String> bindings) throws IOException {
        try (var input = new ZipInputStream(new ByteArrayInputStream(templateContent), StandardCharsets.UTF_8);
             var output = new ByteArrayOutputStream();
             var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                var bytes = input.readAllBytes();
                var copied = new ZipEntry(entry.getName());
                copied.setComment(entry.getComment());
                copied.setExtra(entry.getExtra());
                copied.setTime(entry.getTime());
                zip.putNextEntry(copied);
                if (shouldBind(entry.getName())) {
                    var xml = new String(bytes, StandardCharsets.UTF_8);
                    zip.write(replacePlaceholders(xml, bindings).getBytes(StandardCharsets.UTF_8));
                } else {
                    zip.write(bytes);
                }
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }

    private boolean shouldBind(String entryName) {
        return entryName.startsWith("word/") && entryName.endsWith(".xml");
    }

    private String replacePlaceholders(String xml, Map<String, String> bindings) {
        var replaced = replaceIntactPlaceholders(xml, bindings);
        return replaceSplitTextNodePlaceholders(replaced, bindings);
    }

    private String replaceIntactPlaceholders(String xml, Map<String, String> bindings) {
        var matcher = PLACEHOLDER_PATTERN.matcher(xml);
        var result = new StringBuffer();
        while (matcher.find()) {
            var replacement = bindings.get(matcher.group(1));
            if (replacement == null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(escapeXml(replacement)));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String replaceSplitTextNodePlaceholders(String xml, Map<String, String> bindings) {
        if (bindings.isEmpty() || !xml.contains("${")) {
            return xml;
        }

        var textNodes = extractWordTextNodes(xml);
        if (textNodes.size() < 2) {
            return xml;
        }

        var contents = new ArrayList<String>(textNodes.size());
        var textStarts = new int[textNodes.size()];
        var combined = new StringBuilder();
        for (int i = 0; i < textNodes.size(); i++) {
            var node = textNodes.get(i);
            textStarts[i] = combined.length();
            contents.add(node.content());
            combined.append(node.content());
        }

        var replacements = new ArrayList<SplitPlaceholderReplacement>();
        var matcher = PLACEHOLDER_PATTERN.matcher(combined);
        while (matcher.find()) {
            var replacement = bindings.get(matcher.group(1));
            if (replacement == null) {
                continue;
            }

            var start = locateTextPosition(contents, textStarts, matcher.start());
            var end = locateTextPosition(contents, textStarts, matcher.end() - 1);
            if (start.nodeIndex() == end.nodeIndex()) {
                continue;
            }

            replacements.add(new SplitPlaceholderReplacement(
                    start.nodeIndex(),
                    start.offset(),
                    end.nodeIndex(),
                    end.offset() + 1,
                    escapeXml(replacement)));
        }

        for (int i = replacements.size() - 1; i >= 0; i--) {
            applySplitReplacement(contents, replacements.get(i));
        }
        return rebuildXmlWithTextNodes(xml, textNodes, contents);
    }

    private List<WordTextNode> extractWordTextNodes(String xml) {
        var textNodes = new ArrayList<WordTextNode>();
        var matcher = WORD_TEXT_NODE_PATTERN.matcher(xml);
        while (matcher.find()) {
            textNodes.add(new WordTextNode(matcher.start(2), matcher.end(2), matcher.group(2)));
        }
        return textNodes;
    }

    private TextPosition locateTextPosition(List<String> contents, int[] textStarts, int charIndex) {
        for (int i = 0; i < contents.size(); i++) {
            var start = textStarts[i];
            var end = start + contents.get(i).length();
            if (charIndex >= start && charIndex < end) {
                return new TextPosition(i, charIndex - start);
            }
        }
        throw new IllegalStateException("Placeholder position is outside Word text nodes");
    }

    private void applySplitReplacement(List<String> contents, SplitPlaceholderReplacement replacement) {
        var first = contents.get(replacement.startNodeIndex());
        var last = contents.get(replacement.endNodeIndex());
        contents.set(
                replacement.startNodeIndex(),
                first.substring(0, replacement.startOffset()) + replacement.replacement());
        for (int i = replacement.startNodeIndex() + 1; i < replacement.endNodeIndex(); i++) {
            contents.set(i, "");
        }
        contents.set(replacement.endNodeIndex(), last.substring(replacement.endOffset()));
    }

    private String rebuildXmlWithTextNodes(String xml, List<WordTextNode> textNodes, List<String> contents) {
        var result = new StringBuilder(xml);
        for (int i = textNodes.size() - 1; i >= 0; i--) {
            var node = textNodes.get(i);
            result.replace(node.contentStart(), node.contentEnd(), contents.get(i));
        }
        return result.toString();
    }

    private Map<String, String> buildBindings(DocumentGenerationRequest request) {
        var bindings = new LinkedHashMap<String, String>();
        bindings.put("jobId", valueOrBlank(request.jobId()));
        bindings.put("officeCode", valueOrBlank(request.officeCode()));
        bindings.put("reportId", valueOrBlank(request.reportId()));
        bindings.put("templateCode", valueOrBlank(request.template().templateCode()));
        bindings.put("templateVersion", String.valueOf(request.template().version()));
        bindings.put("generatedAt", OffsetDateTime.now().toString());

        flatten(bindings, "", request.payload());
        addTemplateFieldAliases(bindings);
        addLeafAliases(bindings);
        return bindings;
    }

    private void flatten(Map<String, String> bindings, String prefix, Object value) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> {
                var childKey = prefix.isBlank() ? String.valueOf(key) : prefix + "." + key;
                flatten(bindings, childKey, nested);
            });
            return;
        }
        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                flatten(bindings, prefix + "[" + i + "]", list.get(i));
            }
            return;
        }
        if (!prefix.isBlank()) {
            bindings.put(prefix, valueOrBlank(value));
        }
    }

    private void addTemplateFieldAliases(Map<String, String> bindings) {
        var prefix = "templateFields.";
        var aliases = new LinkedHashMap<String, String>();
        for (var entry : bindings.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            var alias = entry.getKey().substring(prefix.length());
            if (!alias.isBlank() && !alias.contains(".") && !alias.contains("[")) {
                aliases.put(alias, entry.getValue());
            }
        }
        bindings.putAll(aliases);
    }

    private void addLeafAliases(Map<String, String> bindings) {
        var leafCounts = new LinkedHashMap<String, Integer>();
        var leafValues = new LinkedHashMap<String, String>();
        for (var entry : bindings.entrySet()) {
            var leaf = leafName(entry.getKey());
            if (leaf == null || leaf.isBlank()) {
                continue;
            }
            leafCounts.put(leaf, leafCounts.getOrDefault(leaf, 0) + 1);
            leafValues.putIfAbsent(leaf, entry.getValue());
        }
        leafCounts.forEach((leaf, count) -> {
            if (count == 1) {
                bindings.putIfAbsent(leaf, leafValues.get(leaf));
            }
        });
    }

    private String leafName(String key) {
        var dot = key.lastIndexOf('.');
        var bracket = key.lastIndexOf(']');
        var index = Math.max(dot, bracket);
        if (index < 0 || index + 1 >= key.length()) {
            return key;
        }
        return key.substring(index + 1);
    }

    private String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String escapeXml(String value) {
        return valueOrBlank(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String valueOrBlank(Object value) {
        return value == null ? "" : value.toString();
    }

    private record WordTextNode(int contentStart, int contentEnd, String content) {
    }

    private record TextPosition(int nodeIndex, int offset) {
    }

    private record SplitPlaceholderReplacement(
            int startNodeIndex,
            int startOffset,
            int endNodeIndex,
            int endOffset,
            String replacement) {
    }
}
