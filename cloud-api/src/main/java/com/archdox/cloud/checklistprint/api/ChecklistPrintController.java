package com.archdox.cloud.checklistprint.api;

import com.archdox.cloud.checklistprint.application.ChecklistDocxExportService;
import com.archdox.cloud.checklistprint.application.ChecklistPrintReadService;
import com.archdox.cloud.checklistprint.dto.ChecklistPrintResponse;
import com.archdox.cloud.global.security.UserPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inspection-reports")
public class ChecklistPrintController {
    private static final String DOCX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final ChecklistPrintReadService service;
    private final ChecklistDocxExportService docxExportService;

    public ChecklistPrintController(ChecklistPrintReadService service, ChecklistDocxExportService docxExportService) {
        this.service = service;
        this.docxExportService = docxExportService;
    }

    @GetMapping("/{reportId}/checklist-print-preview")
    public ChecklistPrintResponse preview(
            @PathVariable Long reportId,
            @RequestParam(required = false) String type,
            Authentication authentication
    ) {
        return service.preview(reportId, type, (UserPrincipal) authentication.getPrincipal());
    }

    @GetMapping("/{reportId}/checklist-print-docx")
    public ResponseEntity<byte[]> downloadDocx(
            @PathVariable Long reportId,
            @RequestParam(required = false) String type,
            Authentication authentication
    ) {
        var export = docxExportService.export(reportId, type, (UserPrincipal) authentication.getPrincipal());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DOCX_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + export.fileName() + "\"")
                .body(export.content());
    }
}
