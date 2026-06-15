package com.archdox.cloud.engine.inspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.archdox.cloud.global.api.BadRequestException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class InspectionDocumentContentTextServiceTest {
    private final InspectionDocumentContentTextService service = new InspectionDocumentContentTextService();

    @Test
    void resolvesContentTextFirst() {
        var result = service.resolve(Map.of(
                "contentText", "공사감리일지 본문",
                "documentBase64", "invalid"));

        assertThat(result.contentText()).isEqualTo("공사감리일지 본문");
        assertThat(result.metadata())
                .containsEntry("inputMode", "CONTENT_TEXT")
                .containsEntry("inputTextLength", "공사감리일지 본문".length());
    }

    @Test
    void extractsPdfBase64Text() throws Exception {
        var pdf = pdfBase64("Daily log 2021-01-07 excavation depth checked");

        var result = service.resolve(Map.of(
                "documentBase64", pdf,
                "contentType", "application/pdf",
                "fileName", "daily-log.pdf"));

        assertThat(result.contentText()).contains("Daily log 2021-01-07 excavation depth checked");
        assertThat(result.metadata())
                .containsEntry("inputMode", "DOCUMENT_BASE64_PDF")
                .containsEntry("contentType", "application/pdf")
                .containsEntry("pageCount", 1)
                .containsEntry("textExtraction", "PDFBOX_TEXT");
    }

    @Test
    void extractsRepositoryKoreanReferencePdfText() throws Exception {
        Path pdf;
        try (var files = Files.list(referenceFormsDirectory())) {
            pdf = files.filter(path -> path.getFileName().toString().endsWith("21.12.21.pdf"))
                    .findFirst()
                    .orElseThrow();
        }
        var encoded = Base64.getEncoder().encodeToString(Files.readAllBytes(pdf));

        var result = service.resolve(Map.of(
                "documentBase64", encoded,
                "contentType", "application/pdf",
                "fileName", pdf.getFileName().toString()));

        assertThat(result.contentText())
                .contains("공사감리일지")
                .contains("2021년 1월 7일")
                .contains("터파기 깊이 확인");
        assertThat(result.metadata())
                .containsEntry("inputMode", "DOCUMENT_BASE64_PDF")
                .containsEntry("pageCount", 9);
    }

    @Test
    void resolvesPlainTextBase64() {
        var encoded = Base64.getEncoder().encodeToString("감리일지 텍스트".getBytes(StandardCharsets.UTF_8));

        var result = service.resolve(Map.of(
                "documentBase64", encoded,
                "contentType", "text/plain"));

        assertThat(result.contentText()).isEqualTo("감리일지 텍스트");
        assertThat(result.metadata())
                .containsEntry("inputMode", "DOCUMENT_BASE64_TEXT")
                .containsEntry("contentType", "text/plain");
    }

    @Test
    void rejectsMissingContent() {
        assertThatThrownBy(() -> service.resolve(Map.of()))
                .isInstanceOf(BadRequestException.class)
                .extracting("code")
                .isEqualTo("ENGINE_INSPECTION_DOCUMENT_CONTENT_REQUIRED");
    }

    private String pdfBase64(String text) throws Exception {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            try (var contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        }
    }

    private Path referenceFormsDirectory() {
        var current = Path.of("").toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(Path.of("docs", "reference-forms", "korean"));
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("docs/reference-forms/korean not found");
    }
}
