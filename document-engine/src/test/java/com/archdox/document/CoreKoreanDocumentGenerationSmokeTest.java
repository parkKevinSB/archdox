package com.archdox.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class CoreKoreanDocumentGenerationSmokeTest {
    private static final byte[] SAMPLE_PNG = samplePng();

    private final DocxTemplateDocumentEngine engine = new DocxTemplateDocumentEngine(
            spec -> BundledDocumentTemplates.read(spec.storageRef()),
            photo -> Optional.of(new ResolvedPhotoContent(SAMPLE_PNG, "image/png")),
            new SimpleDocumentEngine(),
            new DocumentArtifactExportService(List.of(new FakePdfExporter())));

    @Test
    void constructionDailyLogGeneratesDocxAndPdfFromDefaultTemplate() throws Exception {
        var result = engine.generate(new DocumentGenerationRequest(
                "job-core-daily-1",
                "office-core",
                "report-core-daily-1",
                new TemplateSpec(
                        "KOREAN_CONSTRUCTION_DAILY_SUPERVISION_LOG_APPENDIX_2",
                        1,
                        "templates/korean/korean-construction-daily-supervision-log-appendix-2.docx",
                        "{}",
                        "{}",
                        null,
                        true),
                dailyPayload(),
                samplePhotos(),
                OutputFormat.DOCX_AND_PDF));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        assertEquals(2, result.artifacts().size());

        var docx = artifact(result, ArtifactType.DOCX);
        var pdf = artifact(result, ArtifactType.PDF);
        var documentXml = zipEntry(docx.content(), "word/document.xml");

        assertTrue(documentXml.contains("[별지 제2호서식]"));
        assertTrue(documentXml.contains("공사감리일지"));
        assertTrue(documentXml.contains("일련번호"));
        assertTrue(documentXml.contains("총괄감리책임자"));
        assertTrue(documentXml.contains("건축사보"));
        assertTrue(documentXml.contains("공사명"));
        assertTrue(documentXml.contains("공종 및 세부공정"));
        assertTrue(documentXml.contains("감리 항목"));
        assertTrue(documentXml.contains("감리내용"));
        assertTrue(documentXml.contains("특기사항"));
        assertTrue(documentXml.contains("지적사항 및 처리결과"));
        assertTrue(documentXml.contains("작성방법"));
        assertTrue(documentXml.contains("레퍼런스 타워 신축공사"));
        assertTrue(documentXml.contains("2026"));
        assertTrue(documentXml.contains("25"));
        assertTrue(documentXml.contains("철근 콘크리트 공사"));
        assertTrue(documentXml.contains("기초, 지하층 바닥"));
        assertTrue(documentXml.contains("철근 조립, 배근"));
        assertTrue(documentXml.contains("개구부 주변 안전난간 보강 완료"));
        assertTrue(documentXml.contains("전경 사진"));
        assertTrue(documentXml.contains("사진 및 설명"));
        assertTrue(!documentXml.contains("C9A227"));
        assertTrue(!documentXml.contains("ArchDoxInspectionTable"));
        assertFalse(documentXml.contains("${"));
        assertTrue(zipEntry(docx.content(), "word/_rels/document.xml.rels").contains("rIdArchDoxImage1"));
        assertNotNull(zipEntryBytes(docx.content(), "word/media/archdox-photo-1.png"));
        assertTrue(pdf.fileName().endsWith(".pdf"));
        assertTrue(pdf.bytes() > 0);

        writeSmokeArtifact("construction-daily-supervision-official.docx", docx.content());
    }

    @Test
    void constructionSupervisionReportGeneratesHtmlPreviewAndPdfFromDefaultTemplate() {
        var result = engine.generate(new DocumentGenerationRequest(
                "job-core-report-1",
                "office-core",
                "report-core-supervision-1",
                new TemplateSpec(
                        "KOREAN_CONSTRUCTION_SUPERVISION_REPORT_APPENDIX_1",
                        1,
                        "templates/korean/korean-construction-supervision-report-appendix-1.docx",
                        "{}",
                        "{}",
                        null,
                        true),
                reportPayload(),
                samplePhotos(),
                OutputFormat.HTML_AND_PDF));

        assertEquals(GenerationStatus.COMPLETED, result.status());
        assertEquals(2, result.artifacts().size());

        var html = artifact(result, ArtifactType.HTML);
        var pdf = artifact(result, ArtifactType.PDF);
        var htmlText = new String(html.content(), StandardCharsets.UTF_8);

        assertTrue(htmlText.contains("생성 정보"));
        assertTrue(htmlText.contains("문서 기본사항"));
        assertTrue(htmlText.contains("레퍼런스 타워 신축공사"));
        assertTrue(htmlText.contains("종합의견"));
        assertTrue(htmlText.contains("하중 전달 경로 검토"));
        assertTrue(htmlText.contains("전경 사진"));
        assertFalse(htmlText.contains("${"));
        assertTrue(pdf.fileName().endsWith(".pdf"));
        assertTrue(pdf.bytes() > 0);
    }

    private Map<String, Object> dailyPayload() {
        return Map.of(
                "templateFields", Map.ofEntries(
                        Map.entry("serialNo", "DL-2026-0525-001"),
                        Map.entry("chiefSupervisorName", "김감리"),
                        Map.entry("architectAssistantName", "박건축"),
                        Map.entry("constructionName", "레퍼런스 타워 신축공사"),
                        Map.entry("siteName", "레퍼런스 타워 현장"),
                        Map.entry("inspectionDate", "2026-05-25"),
                        Map.entry("inspectionDayOfWeek", "월요일"),
                        Map.entry("weather", "맑음"),
                        Map.entry("specialNotes", "3층 슬래브 철근 배근 상태를 확인했습니다."),
                        Map.entry("issueAndAction", "개구부 주변 안전난간 보강 완료")),
                "layoutSections", Map.of(
                        "supervisionItemsSection", checklistTable("공사감리 항목", List.of(
                                Map.of("label", "공종", "source", "answer.trade", "width", 3000),
                                Map.of("label", "감리 항목", "source", "label", "width", 2500),
                                Map.of("label", "감리 내용", "source", "note", "width", 3500))),
                        "checklistPhotoSection", checklistPhotoTable(),
                        "photoSection", photoTable()),
                "steps", Map.of(
                        "DAILY_LOG", Map.of(
                                "payload", Map.of(
                                        "dailyItems", Map.of(
                                                "groups", List.of(
                                                        Map.of(
                                                                "trade", "철근 콘크리트 공사",
                                                                "process", "기초, 지하층 바닥",
                                                                "floor", "",
                                                                "items", List.of(
                                                                        Map.of(
                                                                                "item", "철근 조립, 배근",
                                                                                "content", "철근배근의 확인\n- 개수, 철근지름, 피치 확인\n- 정착길이와 굽힘정착 깊이 확인",
                                                                                "photoIds", List.of(1, 2)),
                                                                        Map.of(
                                                                                "item", "철근 규격 증명서",
                                                                                "content", "KS마크 또는 시험성적증명서에 의한 KS규격제품인지 확인",
                                                                                "photoIds", List.of()))),
                                                        Map.of(
                                                                "trade", "가설공사",
                                                                "process", "3층",
                                                                "floor", "",
                                                                "items", List.of(
                                                                        Map.of(
                                                                                "item", "안전난간 설치 상태",
                                                                                "content", "개구부 주변 안전난간 보강 완료",
                                                                                "photoIds", List.of(3))))))))),
                "checklistAnswers", List.of(
                        Map.of(
                                "itemCode", "LOG-001",
                                "label", "슬래브 철근 배근 상태",
                                "answer", Map.of("trade", "철근콘크리트공사 / 3층", "value", "확인"),
                                "note", "배근 간격, 정착 길이, 피복 두께를 확인했습니다."),
                        Map.of(
                                "itemCode", "LOG-002",
                                "label", "안전난간 설치 상태",
                                "answer", Map.of("trade", "가설공사 / 3층", "value", "보완완료"),
                                "note", "개구부 주변 안전난간 보강 완료")),
                "checklistPhotos", List.of(
                        Map.of(
                                "itemCode", "LOG-001",
                                "label", "슬래브 철근 배근 상태",
                                "photoCount", 2,
                                "photoIds", "photo-front, photo-detail")));
    }

    private Map<String, Object> reportPayload() {
        return Map.of(
                "templateFields", Map.ofEntries(
                        Map.entry("permitNumber", "ARCH-2026-001"),
                        Map.entry("permitDate", "2026-05-01"),
                        Map.entry("siteAddress", "서울시 강남구 레퍼런스로 10"),
                        Map.entry("lotNumber", "123-4"),
                        Map.entry("buildingName", "레퍼런스 타워"),
                        Map.entry("constructionName", "레퍼런스 타워 신축공사"),
                        Map.entry("progressType", "중간감리"),
                        Map.entry("supervisionStartDate", "2026-05-01"),
                        Map.entry("supervisionEndDate", "2026-08-31"),
                        Map.entry("chiefSupervisorName", "김감리"),
                        Map.entry("supervisorName", "박점검"),
                        Map.entry("relationEngineerOpinion", "관계전문기술자 의견 검토 완료"),
                        Map.entry("comprehensiveOpinion", "종합의견: 주요 구조 부재와 피난 동선은 적정하며 일부 방화구획 보완 확인이 필요합니다."),
                        Map.entry("reportDate", "2026-05-25"),
                        Map.entry("specialNotes", "방화구획 실란트 성적서 확인 예정")),
                "layoutSections", Map.of(
                        "reportOpinionSection", checklistTable("종합의견", List.of(
                                Map.of("label", "항목", "source", "label", "width", 3000),
                                Map.of("label", "결과", "source", "answer.value", "width", 2000),
                                Map.of("label", "의견", "source", "note", "width", 4000))),
                        "photoSection", photoTable()),
                "checklistAnswers", List.of(
                        Map.of(
                                "itemCode", "RPT-001",
                                "label", "하중 전달 경로 검토",
                                "answer", Map.of("value", "적정"),
                                "note", "기둥, 보, 슬래브 하중 전달 경로를 검토했습니다."),
                        Map.of(
                                "itemCode", "RPT-002",
                                "label", "방화구획 검토",
                                "answer", Map.of("value", "보완 확인 필요"),
                                "note", "준공 전 실란트 자재 성적서를 확인합니다.")));
    }

    private List<PhotoAsset> samplePhotos() {
        return List.of(
                new PhotoAsset("photo-front", "LOG-001", "photos/core/front.png", "전경 사진", PhotoLayoutSize.MEDIUM, "image/png", null),
                new PhotoAsset("photo-detail", "LOG-001", "photos/core/detail.png", "철근 배근 상세", PhotoLayoutSize.MEDIUM, "image/png", null));
    }

    private Map<String, Object> checklistTable(String title, List<?> fields) {
        return new LinkedHashMap<>(Map.of(
                "type", "CHECKLIST_TABLE",
                "title", title,
                "includeTitle", true,
                "tableStyle", "ArchDoxInspectionTable",
                "headerFill", "FFF2CC",
                "borderColor", "C9A227",
                "fields", fields));
    }

    private Map<String, Object> checklistPhotoTable() {
        return new LinkedHashMap<>(Map.of(
                "type", "CHECKLIST_PHOTO_TABLE",
                "title", "체크리스트 사진",
                "includeTitle", true,
                "tableStyle", "ArchDoxInspectionTable",
                "headerFill", "FFF2CC",
                "borderColor", "C9A227",
                "fields", List.of(
                        Map.of("label", "코드", "source", "itemCode", "width", 1600),
                        Map.of("label", "항목", "source", "label", "width", 3600),
                        Map.of("label", "사진 수", "source", "photoCount", "width", 1400),
                        Map.of("label", "사진 ID", "source", "photoIds", "width", 3000))));
    }

    private Map<String, Object> photoTable() {
        return new LinkedHashMap<>(Map.of(
                "type", "PHOTO_TABLE",
                "title", "사진",
                "includeTitle", true,
                "photosPerRow", 2,
                "tableStyle", "ArchDoxPhotoTable",
                "headerFill", "FFF2CC",
                "borderColor", "C9A227",
                "fields", List.of(
                        Map.of("label", "설명", "source", "caption"),
                        Map.of("label", "항목", "source", "checklistItemKey"))));
    }

    private GeneratedArtifact artifact(DocumentGenerationResult result, ArtifactType type) {
        return result.artifacts().stream()
                .filter(artifact -> artifact.type() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError(type + " artifact not found"));
    }

    private String zipEntry(byte[] content, String path) throws IOException {
        var bytes = zipEntryBytes(content, path);
        if (bytes == null) {
            throw new IOException(path + " not found in docx");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private byte[] zipEntryBytes(byte[] content, String path) throws IOException {
        assertNotNull(content);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (path.equals(entry.getName())) {
                    return zip.readAllBytes();
                }
            }
        }
        return null;
    }

    private void writeSmokeArtifact(String fileName, byte[] content) throws IOException {
        var outputDir = Path.of("build", "archdox-smoke");
        Files.createDirectories(outputDir);
        Files.write(outputDir.resolve(fileName), content);
    }

    private static byte[] samplePng() {
        var image = new BufferedImage(640, 420, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(215, 215, 215));
            graphics.setStroke(new BasicStroke(2));
            for (int x = 0; x <= image.getWidth(); x += 80) {
                graphics.drawLine(x, 0, x, image.getHeight());
            }
            for (int y = 0; y <= image.getHeight(); y += 70) {
                graphics.drawLine(0, y, image.getWidth(), y);
            }
            graphics.setColor(new Color(40, 40, 40));
            graphics.setStroke(new BasicStroke(8));
            graphics.drawRect(20, 20, image.getWidth() - 40, image.getHeight() - 40);
            graphics.setColor(new Color(70, 100, 140));
            graphics.drawRect(80, 90, 480, 240);
            graphics.setColor(new Color(55, 110, 60));
            graphics.drawLine(80, 330, 260, 180);
            graphics.drawLine(260, 180, 390, 260);
            graphics.drawLine(390, 260, 560, 110);
            graphics.setFont(new Font("SansSerif", Font.BOLD, 28));
            graphics.setColor(new Color(30, 30, 30));
            graphics.drawString("ARCHDOX SAMPLE PHOTO", 180, 205);
        } finally {
            graphics.dispose();
        }
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create sample PNG", ex);
        }
    }

    private static final class FakePdfExporter implements DocumentArtifactExporter {
        @Override
        public boolean supports(ArtifactType sourceType, ArtifactType targetType) {
            return sourceType == ArtifactType.DOCX && targetType == ArtifactType.PDF;
        }

        @Override
        public DocumentExportResult export(DocumentExportRequest request) {
            var content = ("%PDF-1.4\n% ArchDox fake PDF for " + request.reportId() + "\n").getBytes(StandardCharsets.UTF_8);
            var artifact = new GeneratedArtifact(
                    ArtifactType.PDF,
                    request.reportId() + ".pdf",
                    "documents/jobs/" + request.jobId() + "/" + request.reportId() + ".pdf",
                    content.length,
                    sha256(content),
                    content);
            return DocumentExportResult.completed(artifact);
        }

        private static String sha256(byte[] content) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 is not available", ex);
            }
        }
    }
}
