package com.archdox.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class HtmlPreviewDocumentRenderer {
    private final PhotoContentResolver photoContentResolver;

    public HtmlPreviewDocumentRenderer() {
        this(photo -> Optional.empty());
    }

    public HtmlPreviewDocumentRenderer(PhotoContentResolver photoContentResolver) {
        this.photoContentResolver = photoContentResolver == null ? photo -> Optional.empty() : photoContentResolver;
    }

    public GeneratedArtifact render(DocumentGenerationRequest request) {
        var fileName = "inspection-report-" + sanitizeFileName(request.reportId()) + ".html";
        var storageRef = "documents/jobs/" + sanitizeFileName(request.jobId()) + "/" + fileName;
        var content = html(request).getBytes(StandardCharsets.UTF_8);
        return new GeneratedArtifact(
                ArtifactType.HTML,
                fileName,
                storageRef,
                content.length,
                sha256(content),
                content);
    }

    private String html(DocumentGenerationRequest request) {
        var title = firstText(
                readTextPath(request.payload(), "templateFields.documentTitle"),
                readTextPath(request.payload(), "templateFields.reportTitle"),
                readTextPath(request.payload(), "templateFields.constructionName"),
                readTextPath(request.payload(), "templateFields.projectName"),
                Optional.ofNullable(request.template().templateCode()),
                Optional.of("ArchDox Document Preview"));
        var body = new StringBuilder();
        body.append("<section class=\"hero\">")
                .append("<p class=\"eyebrow\">ArchDox Preview</p>")
                .append("<h1>").append(escapeHtml(title)).append("</h1>")
                .append("<p class=\"muted\">").append(escapeHtml(OffsetDateTime.now().toString())).append("</p>")
                .append("</section>");
        body.append(templateFieldsSection(request));
        body.append(dailySupervisionSection(request));
        body.append(layoutSections(request));
        body.append(defaultPhotoSection(request));
        body.append(defaultChecklistSection(request));
        body.append(signatureSection(request));
        body.append(metadataSection(request));
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>__TITLE__</title>
                  <style>
                    :root {
                      color-scheme: light;
                      --bg: #f6f7f9;
                      --paper: #ffffff;
                      --ink: #18202f;
                      --muted: #667085;
                      --line: #dde2ea;
                      --soft: #f1f4f8;
                      --accent: #c9a227;
                      --accent-soft: #fff5d6;
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0;
                      background: var(--bg);
                      color: var(--ink);
                      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans KR", sans-serif;
                      font-size: 14px;
                      line-height: 1.55;
                    }
                    main {
                      width: min(100%, 960px);
                      margin: 0 auto;
                      padding: 24px;
                    }
                    .paper {
                      background: var(--paper);
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      box-shadow: 0 18px 45px rgba(15, 23, 42, 0.07);
                      padding: 32px;
                    }
                    .hero {
                      border-bottom: 2px solid var(--ink);
                      margin-bottom: 24px;
                      padding-bottom: 18px;
                    }
                    .eyebrow {
                      color: var(--accent);
                      font-size: 12px;
                      font-weight: 700;
                      letter-spacing: 0;
                      margin: 0 0 6px;
                      text-transform: uppercase;
                    }
                    h1 {
                      font-size: 28px;
                      line-height: 1.2;
                      margin: 0 0 8px;
                    }
                    h2 {
                      font-size: 18px;
                      margin: 28px 0 12px;
                      padding-left: 10px;
                      border-left: 4px solid var(--accent);
                    }
                    table {
                      width: 100%;
                      border-collapse: collapse;
                      table-layout: fixed;
                      margin: 8px 0 18px;
                    }
                    th, td {
                      border: 1px solid var(--line);
                      padding: 9px 10px;
                      text-align: left;
                      vertical-align: top;
                      word-break: break-word;
                    }
                    th {
                      background: var(--soft);
                      font-weight: 700;
                    }
                    .muted { color: var(--muted); }
                    .field-grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 10px;
                    }
                    .field {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 10px 12px;
                      background: #fff;
                    }
                    .field-label {
                      color: var(--muted);
                      font-size: 12px;
                      margin-bottom: 4px;
                    }
                    .photo-grid {
                      display: grid;
                      grid-template-columns: repeat(var(--columns, 2), minmax(0, 1fr));
                      gap: 12px;
                    }
                    .photo-card {
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      overflow: hidden;
                      background: #fff;
                    }
                    .photo-card img {
                      display: block;
                      width: 100%;
                      aspect-ratio: 4 / 3;
                      object-fit: cover;
                      background: var(--soft);
                    }
                    .photo-empty {
                      display: grid;
                      place-items: center;
                      width: 100%;
                      aspect-ratio: 4 / 3;
                      color: var(--muted);
                      background: var(--soft);
                      padding: 12px;
                      text-align: center;
                    }
                    .photo-meta { padding: 10px 12px; }
                    .badge {
                      display: inline-block;
                      border-radius: 999px;
                      background: var(--accent-soft);
                      color: #725a12;
                      font-size: 12px;
                      font-weight: 700;
                      padding: 2px 8px;
                      margin-bottom: 6px;
                    }
                    .signature-box {
                      display: grid;
                      grid-template-columns: minmax(180px, 260px) minmax(0, 1fr);
                      gap: 16px;
                      align-items: center;
                      border: 1px solid var(--line);
                      border-radius: 8px;
                      padding: 14px;
                      background: #fff;
                    }
                    .signature-image {
                      display: grid;
                      place-items: center;
                      min-height: 112px;
                      border: 1px dashed #c9d2dc;
                      border-radius: 8px;
                      background: #fbfcfd;
                    }
                    .signature-image img {
                      display: block;
                      max-width: 100%;
                      max-height: 100px;
                      object-fit: contain;
                    }
                    .signature-meta {
                      display: grid;
                      gap: 6px;
                    }
                    @media (max-width: 720px) {
                      main { padding: 12px; }
                      .paper { padding: 18px; border-radius: 0; }
                      h1 { font-size: 23px; }
                      .field-grid { grid-template-columns: 1fr; }
                      .photo-grid { grid-template-columns: 1fr; }
                      .signature-box { grid-template-columns: 1fr; }
                    }
                  </style>
                </head>
                <body>
                  <main>
                    <article class="paper">
                      __BODY__
                    </article>
                  </main>
                </body>
                </html>
                """
                .replace("__TITLE__", escapeHtml(title))
                .replace("__BODY__", body.toString());
    }

    private String signatureSection(DocumentGenerationRequest request) {
        var signature = mapValue(request.payload().get("signature"));
        if (!Boolean.TRUE.equals(signature.get("signed")) || !shouldRenderDefaultSignature(request)) {
            return "";
        }
        var name = valueOrBlank(signature.get("signedByName"));
        var role = valueOrBlank(signature.get("signedByRole"));
        var signedAt = valueOrBlank(signature.get("signedAt"));
        var imageDataUrl = signatureImageDataUrl(signature);
        var image = imageDataUrl.isBlank()
                ? "<span class=\"muted\">서명 이미지 없음</span>"
                : "<img alt=\"서명\" src=\"%s\">".formatted(escapeHtml(imageDataUrl));
        return """
                <section>
                  <h2>서명</h2>
                  <div class="signature-box">
                    <div class="signature-image">%s</div>
                    <div class="signature-meta">
                      %s
                      %s
                      %s
                    </div>
                  </div>
                </section>
                """.formatted(
                image,
                tableLikeLine("서명자", name),
                role.isBlank() ? "" : tableLikeLine("역할", role),
                signedAt.isBlank() ? "" : tableLikeLine("서명일시", signedAt));
    }

    private String tableLikeLine(String label, String value) {
        return "<div><strong>%s</strong>: %s</div>".formatted(escapeHtml(label), escapeHtml(value));
    }

    private String signatureImageDataUrl(Map<String, Object> signature) {
        var dataUrl = stringValue(signature.get("imageDataUrl"));
        if (dataUrl == null || !dataUrl.startsWith("data:image/") || !dataUrl.contains(";base64,")) {
            return "";
        }
        return dataUrl;
    }

    private boolean shouldRenderDefaultSignature(DocumentGenerationRequest request) {
        var report = mapValue(request.payload().get("report"));
        var documentType = mapValue(request.payload().get("documentType"));
        return isDailySupervisionType(stringValue(report.get("reportType")))
                || isDailySupervisionType(stringValue(documentType.get("reportType")))
                || isDailySupervisionType(stringValue(documentType.get("code")));
    }

    private boolean isDailySupervisionType(String value) {
        var code = normalizeCode(value);
        return "DAILY_SUPERVISION".equals(code) || code.contains("DAILY_SUPERVISION_LOG");
    }

    private String metadataSection(DocumentGenerationRequest request) {
        return """
                <section>
                  <h2>생성 정보</h2>
                  <table>
                    <tbody>
                      %s
                      %s
                      %s
                      %s
                    </tbody>
                  </table>
                </section>
                """.formatted(
                tableRow("Job ID", request.jobId()),
                tableRow("Office", request.officeCode()),
                tableRow("Report ID", request.reportId()),
                tableRow("Template", request.template().templateCode() + " v" + request.template().version()));
    }

    private String templateFieldsSection(DocumentGenerationRequest request) {
        var fields = mapValue(request.payload().get("templateFields"));
        if (fields.isEmpty()) {
            return "";
        }
        var items = new StringBuilder();
        for (var field : displayFieldLabels().entrySet()) {
            var value = fields.get(field.getKey());
            if (!isSimpleValue(value)) {
                continue;
            }
            var text = valueOrBlank(value).trim();
            if (text.isBlank()) {
                continue;
            }
            items.append("""
                    <div class="field">
                      <div class="field-label">%s</div>
                      <div>%s</div>
                    </div>
                    """.formatted(escapeHtml(field.getValue()), escapeHtml(text)));
        }
        if (items.isEmpty()) {
            return "";
        }
        return """
                <section>
                  <h2>문서 기본사항</h2>
                  <div class="field-grid">%s</div>
                </section>
                """.formatted(items);
    }

    private Map<String, String> displayFieldLabels() {
        var labels = new LinkedHashMap<String, String>();
        labels.put("constructionName", "공사명");
        labels.put("constructionProjectName", "공사명");
        labels.put("projectName", "프로젝트");
        labels.put("siteName", "현장명");
        labels.put("siteAddress", "현장 주소");
        labels.put("inspectionLocation", "점검 위치");
        labels.put("buildingName", "건축물명");
        labels.put("lotNumber", "대지 위치");
        labels.put("permitNumber", "허가 번호");
        labels.put("permitDate", "허가일");
        labels.put("inspectionDate", "점검일");
        labels.put("safetyInspectionDate", "안전점검일");
        labels.put("reportDate", "보고일");
        labels.put("constructionStartDate", "공사 시작일");
        labels.put("supervisionStartDate", "감리 시작일");
        labels.put("supervisionEndDate", "감리 종료일");
        labels.put("chiefSupervisorName", "총괄 감리자");
        labels.put("supervisorName", "감리자");
        labels.put("supervisorOfficeName", "감리 사무소");
        labels.put("assistantSupervisorName", "보조 감리자");
        labels.put("inspectorName", "점검자");
        labels.put("architectAssistantName", "건축사보");
        labels.put("assistantArchitectName", "보조 건축사");
        labels.put("demolitionWorkerName", "해체 작업자");
        labels.put("contractorName", "시공자");
        labels.put("progressType", "진행 구분");
        labels.put("safetyCheckStage", "안전점검 단계");
        labels.put("weather", "날씨");
        labels.put("inspectionCriteria", "점검 기준");
        labels.put("inspectionResult", "점검 결과");
        labels.put("correctiveAction", "조치사항");
        labels.put("relationEngineerOpinion", "관계전문기술자 의견");
        labels.put("comprehensiveOpinion", "종합 의견");
        labels.put("specialNotes", "특이사항");
        labels.put("issueAndAction", "지적사항 및 처리결과");
        labels.put("correctionResults", "지적사항 및 처리결과");
        labels.put("nextAction", "다음 조치");
        return labels;
    }

    private String layoutSections(DocumentGenerationRequest request) {
        var layoutSections = mapValue(request.payload().get("layoutSections"));
        if (layoutSections.isEmpty()) {
            return "";
        }
        var rendered = new StringBuilder();
        layoutSections.forEach((key, value) -> {
            var section = mapValue(value);
            var type = normalizeCode(stringValue(section.get("type")));
            if ("PHOTO_TABLE".equals(type)) {
                rendered.append(photoSection(section, request.photos()));
            } else if ("CHECKLIST_TABLE".equals(type)) {
                rendered.append(checklistSection(section, listValue(request.payload().get("checklistAnswers"))));
            } else if ("CHECKLIST_PHOTO_TABLE".equals(type)) {
                rendered.append(checklistSection(section, listValue(request.payload().get("checklistPhotos"))));
            }
        });
        return rendered.toString();
    }

    private String dailySupervisionSection(DocumentGenerationRequest request) {
        var dailyItems = mapValue(readPath(request.payload(), "steps.DAILY_LOG.payload.dailyItems").orElse(null));
        var groups = listValue(dailyItems.get("groups"));
        if (groups.isEmpty()) {
            return "";
        }
        var rows = new StringBuilder();
        rows.append("<thead><tr>")
                .append("<th>공종 및 세부공정</th>")
                .append("<th>감리 항목</th>")
                .append("<th>감리내용</th>")
                .append("<th>사진</th>")
                .append("</tr></thead><tbody>");
        var renderedAny = false;
        for (var rawGroup : groups) {
            var group = mapValue(rawGroup);
            var groupLabel = joinNonBlank(
                    valueOrBlank(group.get("tradeName")),
                    valueOrBlank(group.get("processName")),
                    valueOrBlank(group.get("floor")));
            var entries = listValue(group.get("entries"));
            if (entries.isEmpty()) {
                rows.append("<tr><td>")
                        .append(escapeHtml(groupLabel))
                        .append("</td><td></td><td></td><td></td></tr>");
                renderedAny = true;
                continue;
            }
            for (var rawEntry : entries) {
                var entry = mapValue(rawEntry);
                var itemName = valueOrBlank(entry.get("inspectionItemName"));
                var content = dailySupervisionContent(entry);
                var photoIds = joinValues(listValue(entry.get("photoIds")));
                if (groupLabel.isBlank() && itemName.isBlank() && content.isBlank() && photoIds.isBlank()) {
                    continue;
                }
                rows.append("<tr><td>")
                        .append(escapeHtml(groupLabel))
                        .append("</td><td>")
                        .append(escapeHtml(itemName))
                        .append("</td><td>")
                        .append(escapeHtml(content))
                        .append("</td><td>")
                        .append(escapeHtml(photoIds))
                        .append("</td></tr>");
                renderedAny = true;
            }
        }
        rows.append("</tbody>");
        if (!renderedAny) {
            return "";
        }
        return """
                <section>
                  <h2>공종별 검사항목</h2>
                  <table>%s</table>
                </section>
                """.formatted(rows);
    }

    private String dailySupervisionContent(Map<String, Object> entry) {
        var rows = new ArrayList<String>();
        for (Object rowValue : listValue(entry.get("checklistRows"))) {
            var row = mapValue(rowValue);
            var rowContent = dailyChecklistRowContent(row);
            if (!rowContent.isBlank()) {
                rows.add("- " + rowContent);
            }
        }
        if (rows.isEmpty()) {
            return "";
        }
        var title = valueOrBlank(entry.get("inspectionItemName"));
        if (!title.isBlank()) {
            rows.add(0, title);
        }
        return String.join("\n", rows);
    }

    private String dailyChecklistRowContent(Map<String, Object> row) {
        var label = valueOrBlank(row.get("label"));
        var result = dailyChecklistResultLabel(valueOrBlank(row.get("result")));
        var referenceNote = valueOrBlank(row.get("referenceNote"));
        var actionNote = valueOrBlank(row.get("actionNote"));
        var parts = new ArrayList<String>();
        if (!label.isBlank()) {
            parts.add(label);
        }
        if (!result.isBlank()) {
            parts.add(result);
        }
        if (!referenceNote.isBlank()) {
            parts.add("기준·참고: " + referenceNote);
        }
        if (!actionNote.isBlank()) {
            parts.add("조치사항: " + actionNote);
        }
        return parts.size() <= 1 ? "" : String.join(" / ", parts);
    }

    private String dailyChecklistResultLabel(String result) {
        return switch (result.trim().toUpperCase(Locale.ROOT)) {
            case "COMPLIANT" -> "적합";
            case "NON_COMPLIANT" -> "부적합";
            case "NOT_APPLICABLE" -> "";
            default -> "";
        };
    }

    private String defaultPhotoSection(DocumentGenerationRequest request) {
        if (!mapValue(request.payload().get("layoutSections")).isEmpty()) {
            return "";
        }
        var photos = request.photos() == null ? List.<PhotoAsset>of() : request.photos();
        if (photos.isEmpty()) {
            return "";
        }
        return photoSection(Map.of("title", "사진", "photosPerRow", 2), photos);
    }

    private String defaultChecklistSection(DocumentGenerationRequest request) {
        if (!mapValue(request.payload().get("layoutSections")).isEmpty()) {
            return "";
        }
        var answers = listValue(request.payload().get("checklistAnswers"));
        if (answers.isEmpty()) {
            return "";
        }
        return checklistSection(Map.of("title", "체크리스트"), answers);
    }

    private String photoSection(Map<String, Object> section, List<PhotoAsset> rawPhotos) {
        var photos = rawPhotos == null ? List.<PhotoAsset>of() : rawPhotos;
        var title = firstText(Optional.ofNullable(stringValue(section.get("title"))), Optional.of("사진"));
        var columns = Math.max(1, Math.min(4, intValue(section.get("photosPerRow"), 2)));
        var cards = new StringBuilder();
        if (photos.isEmpty()) {
            cards.append("<p class=\"muted\">").append(escapeHtml(emptyText(section, "등록된 사진이 없습니다."))).append("</p>");
        } else {
            var fields = sectionFields(section, defaultPhotoDescriptionFields());
            for (var photo : photos) {
                cards.append(photoCard(photo, fields));
            }
        }
        return """
                <section>
                  <h2>%s</h2>
                  <div class="photo-grid" style="--columns: %d">%s</div>
                </section>
                """.formatted(escapeHtml(title), columns, cards);
    }

    private String photoCard(PhotoAsset photo, List<Map<String, String>> fields) {
        var image = photoImage(photo);
        var metadata = new StringBuilder();
        metadata.append("<div class=\"photo-meta\">");
        metadata.append("<div class=\"badge\">Photo ").append(escapeHtml(valueOrBlank(photo.photoId()))).append("</div>");
        var seenValues = new HashSet<String>();
        for (var field : fields) {
            var label = PhotoDisplayTexts.label(field, photo);
            var value = photoFieldValue(photo, field.get("source"));
            if (!value.isBlank()) {
                if (!seenValues.add(value.trim())) {
                    continue;
                }
                metadata.append("<div><strong>")
                        .append(escapeHtml(label))
                        .append("</strong>: ")
                        .append(escapeHtml(value))
                        .append("</div>");
            }
        }
        metadata.append("</div>");
        return """
                <figure class="photo-card">
                  %s
                  %s
                </figure>
                """.formatted(image, metadata);
    }

    private String photoImage(PhotoAsset photo) {
        var dataUrl = photoDataUrl(photo);
        if (dataUrl.isPresent()) {
            return "<img alt=\"%s\" src=\"%s\">".formatted(
                    escapeHtml(firstText(Optional.ofNullable(photo.caption()), Optional.ofNullable(photo.photoId()))),
                    dataUrl.get());
        }
        return """
                <div class="photo-empty">
                  <div>이미지 미리보기 없음<br><span class="muted">%s</span></div>
                </div>
                """.formatted(escapeHtml(valueOrBlank(photo.storageRef())));
    }

    private Optional<String> photoDataUrl(PhotoAsset photo) {
        try {
            return photoContentResolver.resolve(photo)
                    .map(content -> {
                        var mimeType = content.mimeType() == null || content.mimeType().isBlank()
                                ? "image/jpeg"
                                : content.mimeType();
                        return "data:%s;base64,%s".formatted(
                                escapeHtml(mimeType),
                                Base64.getEncoder().encodeToString(content.content()));
                    });
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private String checklistSection(Map<String, Object> section, List<?> answers) {
        var title = firstText(Optional.ofNullable(stringValue(section.get("title"))), Optional.of("체크리스트"));
        var fields = sectionFields(
                section,
                "CHECKLIST_PHOTO_TABLE".equals(normalizeCode(stringValue(section.get("type"))))
                        ? defaultChecklistPhotoTableFields()
                        : defaultChecklistTableFields());
        var rows = new StringBuilder();
        rows.append("<thead><tr>");
        for (var field : fields) {
            rows.append("<th>").append(escapeHtml(field.getOrDefault("label", field.getOrDefault("source", "")))).append("</th>");
        }
        rows.append("</tr></thead><tbody>");
        if (answers.isEmpty()) {
            rows.append("<tr><td colspan=\"")
                    .append(fields.size())
                    .append("\" class=\"muted\">")
                    .append(escapeHtml(emptyText(section, "저장된 체크리스트 응답이 없습니다.")))
                    .append("</td></tr>");
        } else {
            for (var answer : answers) {
                rows.append("<tr>");
                for (var field : fields) {
                    rows.append("<td>")
                            .append(escapeHtml(checklistFieldValue(answer, field.get("source"))))
                            .append("</td>");
                }
                rows.append("</tr>");
            }
        }
        rows.append("</tbody>");
        return """
                <section>
                  <h2>%s</h2>
                  <table>%s</table>
                </section>
                """.formatted(escapeHtml(title), rows);
    }

    private String tableRow(String label, String value) {
        return "<tr><th>%s</th><td>%s</td></tr>".formatted(escapeHtml(label), escapeHtml(valueOrBlank(value)));
    }

    private List<Map<String, String>> sectionFields(Map<String, Object> section, List<Map<String, String>> fallback) {
        var rawFields = listValue(section.get("fields"));
        if (rawFields.isEmpty()) {
            return fallback;
        }
        var fields = new ArrayList<Map<String, String>>();
        for (var rawField : rawFields) {
            var field = mapValue(rawField);
            var source = stringValue(field.get("source"));
            if (source == null || source.isBlank()) {
                continue;
            }
            var mapped = new LinkedHashMap<String, String>();
            mapped.put("source", source);
            mapped.put("label", firstText(Optional.ofNullable(stringValue(field.get("label"))), Optional.of(source)));
            fields.add(mapped);
        }
        return fields.isEmpty() ? fallback : fields;
    }

    private List<Map<String, String>> defaultPhotoDescriptionFields() {
        return List.of(
                Map.of("label", "설명", "source", "caption"),
                Map.of("label", "항목", "source", "checklistItemKey"));
    }

    private List<Map<String, String>> defaultChecklistTableFields() {
        return List.of(
                Map.of("label", "코드", "source", "itemCode"),
                Map.of("label", "항목", "source", "label"),
                Map.of("label", "결과", "source", "answer.value"),
                Map.of("label", "사진", "source", "photoCount"),
                Map.of("label", "비고", "source", "note"));
    }

    private List<Map<String, String>> defaultChecklistPhotoTableFields() {
        return List.of(
                Map.of("label", "코드", "source", "itemCode"),
                Map.of("label", "항목", "source", "label"),
                Map.of("label", "사진 수", "source", "photoCount"),
                Map.of("label", "사진 ID", "source", "photoIds"));
    }

    private String photoFieldValue(PhotoAsset photo, String source) {
        return PhotoDisplayTexts.value(photo, source);
    }

    private String checklistFieldValue(Object answer, String source) {
        return readPath(answer, source).map(this::valueOrBlank).orElse("");
    }

    @SafeVarargs
    private String firstText(Optional<String>... values) {
        for (var value : values) {
            if (value.isPresent() && !value.get().isBlank()) {
                return value.get();
            }
        }
        return "";
    }

    private Optional<Object> readPath(Object root, String path) {
        if (root == null || path == null || path.isBlank()) {
            return Optional.empty();
        }
        Object current = root;
        for (var segment : path.split("\\.")) {
            current = readSegment(current, segment);
            if (current == null) {
                return Optional.empty();
            }
        }
        return Optional.of(current);
    }

    private Optional<String> readTextPath(Object root, String path) {
        return readPath(root, path)
                .map(this::valueOrBlank)
                .filter(value -> !value.isBlank());
    }

    private Object readSegment(Object current, String segment) {
        if (segment == null || segment.isBlank()) {
            return null;
        }
        var key = segment;
        Integer index = null;
        var bracketStart = segment.indexOf('[');
        if (bracketStart >= 0 && segment.endsWith("]")) {
            key = segment.substring(0, bracketStart);
            var rawIndex = segment.substring(bracketStart + 1, segment.length() - 1);
            try {
                index = Integer.parseInt(rawIndex);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        Object value = current;
        if (!key.isBlank()) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            value = map.get(key);
        }
        if (index == null) {
            return value;
        }
        if (value instanceof List<?> list && index >= 0 && index < list.size()) {
            return list.get(index);
        }
        return null;
    }

    private boolean isSimpleValue(Object value) {
        return value == null || value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    private String emptyText(Map<String, Object> section, String fallback) {
        var text = stringValue(section.get("emptyText"));
        if (text == null) {
            text = stringValue(section.get("emptyMessage"));
        }
        return text == null ? fallback : text;
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, Object>();
        raw.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private String stringValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value);
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
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

    private String escapeHtml(String value) {
        return valueOrBlank(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String joinNonBlank(String... values) {
        var result = new ArrayList<String>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return String.join(" / ", result);
    }

    private String joinValues(List<?> values) {
        var result = new ArrayList<String>();
        for (Object value : values) {
            var text = valueOrBlank(value).trim();
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return String.join(", ", result);
    }

    private String valueOrBlank(Object value) {
        return value == null ? "" : value.toString();
    }
}
