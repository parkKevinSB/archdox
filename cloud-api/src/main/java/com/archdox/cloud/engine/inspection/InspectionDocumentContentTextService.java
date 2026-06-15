package com.archdox.cloud.engine.inspection;

import com.archdox.cloud.global.api.BadRequestException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
public class InspectionDocumentContentTextService {
    private static final int MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 500_000;

    public ResolvedInspectionDocumentContent resolve(Map<String, Object> arguments) {
        var contentText = text(arguments.get("contentText"));
        if (!contentText.isBlank()) {
            return new ResolvedInspectionDocumentContent(contentText, Map.of(
                    "inputMode", "CONTENT_TEXT",
                    "inputTextLength", contentText.length()));
        }

        var documentBase64 = text(arguments.get("documentBase64"));
        if (documentBase64.isBlank()) {
            throw new BadRequestException(
                    "ENGINE_INSPECTION_DOCUMENT_CONTENT_REQUIRED",
                    "errors.engine.inspectionDocument.contentRequired",
                    "contentText or documentBase64 is required");
        }
        var bytes = decodeBase64(documentBase64);
        var contentType = normalizedContentType(text(arguments.get("contentType")), text(arguments.get("fileName")));
        if ("application/pdf".equals(contentType)) {
            return extractPdf(bytes, contentType);
        }
        if ("text/plain".equals(contentType)) {
            var text = limitText(new String(bytes, StandardCharsets.UTF_8));
            if (text.isBlank()) {
                throw emptyText("Plain text document has no extractable text");
            }
            return new ResolvedInspectionDocumentContent(text, Map.of(
                    "inputMode", "DOCUMENT_BASE64_TEXT",
                    "contentType", contentType,
                    "byteLength", bytes.length,
                    "inputTextLength", text.length()));
        }
        throw new BadRequestException(
                "ENGINE_INSPECTION_DOCUMENT_UNSUPPORTED_CONTENT_TYPE",
                "errors.engine.inspectionDocument.unsupportedContentType",
                "Only contentText, text/plain documentBase64, or application/pdf documentBase64 are supported",
                Map.of("contentType", contentType));
    }

    private ResolvedInspectionDocumentContent extractPdf(byte[] bytes, String contentType) {
        try (var document = Loader.loadPDF(bytes)) {
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            var text = limitText(stripper.getText(document));
            if (text.isBlank()) {
                throw emptyText("PDF document has no extractable text");
            }
            var metadata = new LinkedHashMap<String, Object>();
            metadata.put("inputMode", "DOCUMENT_BASE64_PDF");
            metadata.put("contentType", contentType);
            metadata.put("byteLength", bytes.length);
            metadata.put("pageCount", document.getNumberOfPages());
            metadata.put("inputTextLength", text.length());
            metadata.put("textExtraction", "PDFBOX_TEXT");
            return new ResolvedInspectionDocumentContent(text, Map.copyOf(metadata));
        } catch (IOException ex) {
            throw new BadRequestException(
                    "ENGINE_INSPECTION_DOCUMENT_PDF_TEXT_EXTRACTION_FAILED",
                    "errors.engine.inspectionDocument.pdfTextExtractionFailed",
                    "Failed to extract text from PDF document",
                    Map.of("error", ex.getClass().getSimpleName()));
        }
    }

    private byte[] decodeBase64(String value) {
        var normalized = stripDataUrl(value).replaceAll("\\s+", "");
        try {
            var decoded = Base64.getDecoder().decode(normalized);
            if (decoded.length > MAX_DOCUMENT_BYTES) {
                throw new BadRequestException(
                        "ENGINE_INSPECTION_DOCUMENT_TOO_LARGE",
                        "errors.engine.inspectionDocument.tooLarge",
                        "documentBase64 exceeds the maximum supported size",
                        Map.of(
                                "maxBytes", MAX_DOCUMENT_BYTES,
                                "actualBytes", decoded.length));
            }
            return decoded;
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(
                    "ENGINE_INSPECTION_DOCUMENT_INVALID_BASE64",
                    "errors.engine.inspectionDocument.invalidBase64",
                    "documentBase64 must be valid Base64");
        }
    }

    private String stripDataUrl(String value) {
        var comma = value.indexOf(',');
        if (value.regionMatches(true, 0, "data:", 0, 5) && comma >= 0) {
            return value.substring(comma + 1);
        }
        return value;
    }

    private String normalizedContentType(String contentType, String fileName) {
        var value = contentType == null ? "" : contentType.trim().toLowerCase();
        var semicolon = value.indexOf(';');
        if (semicolon >= 0) {
            value = value.substring(0, semicolon).trim();
        }
        if (!value.isBlank()) {
            return value;
        }
        var lowerFileName = fileName == null ? "" : fileName.trim().toLowerCase();
        if (lowerFileName.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lowerFileName.endsWith(".txt")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    private String limitText(String value) {
        var text = value == null ? "" : value.trim();
        if (text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH);
    }

    private BadRequestException emptyText(String message) {
        return new BadRequestException(
                "ENGINE_INSPECTION_DOCUMENT_TEXT_EMPTY",
                "errors.engine.inspectionDocument.textEmpty",
                message);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record ResolvedInspectionDocumentContent(
            String contentText,
            Map<String, Object> metadata
    ) {
        public ResolvedInspectionDocumentContent {
            contentText = contentText == null ? "" : contentText.trim();
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }
}
