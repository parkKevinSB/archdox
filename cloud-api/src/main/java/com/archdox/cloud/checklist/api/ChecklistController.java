package com.archdox.cloud.checklist.api;

import com.archdox.cloud.checklist.application.ChecklistService;
import com.archdox.cloud.checklist.dto.ChecklistAnswerResponse;
import com.archdox.cloud.checklist.dto.ReportChecklistResponse;
import com.archdox.cloud.checklist.dto.SaveChecklistAnswerRequest;
import com.archdox.cloud.global.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inspection-reports/{reportId}/checklist")
public class ChecklistController {
    private final ChecklistService checklistService;

    public ChecklistController(ChecklistService checklistService) {
        this.checklistService = checklistService;
    }

    @GetMapping
    public ReportChecklistResponse getReportChecklist(@PathVariable Long reportId) {
        return checklistService.getReportChecklist(reportId);
    }

    @PutMapping("/answers/{itemCode}")
    public ChecklistAnswerResponse saveAnswer(
            @PathVariable Long reportId,
            @PathVariable String itemCode,
            @Valid @RequestBody SaveChecklistAnswerRequest request,
            Authentication authentication
    ) {
        return checklistService.saveAnswer(
                reportId,
                itemCode,
                request,
                (UserPrincipal) authentication.getPrincipal());
    }
}
