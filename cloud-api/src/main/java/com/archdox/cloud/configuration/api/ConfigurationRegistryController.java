package com.archdox.cloud.configuration.api;

import com.archdox.cloud.configuration.application.ConfigurationRegistryService;
import com.archdox.cloud.configuration.dto.ConfigDefinitionResponse;
import com.archdox.cloud.configuration.dto.CreateConfigDefinitionRequest;
import com.archdox.cloud.configuration.dto.CreateDocumentTemplateRevisionRequest;
import com.archdox.cloud.configuration.dto.CreateJsonConfigRevisionRequest;
import com.archdox.cloud.configuration.dto.DocumentTemplateRevisionResponse;
import com.archdox.cloud.configuration.dto.JsonConfigRevisionResponse;
import com.archdox.cloud.configuration.dto.OfficeConfigOverrideResponse;
import com.archdox.cloud.configuration.dto.ResolvedOfficeConfigurationResponse;
import com.archdox.cloud.configuration.dto.UpdateOfficeConfigOverrideRequest;
import com.archdox.cloud.global.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/config")
public class ConfigurationRegistryController {
    private final ConfigurationRegistryService service;

    public ConfigurationRegistryController(ConfigurationRegistryService service) {
        this.service = service;
    }

    @GetMapping("/document-templates")
    public List<ConfigDefinitionResponse> documentTemplates(
            Authentication authentication,
            @RequestParam(required = false) String reportType
    ) {
        return service.listDocumentTemplates(principal(authentication), reportType);
    }

    @PostMapping("/document-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigDefinitionResponse createDocumentTemplate(
            Authentication authentication,
            @Valid @RequestBody CreateConfigDefinitionRequest request
    ) {
        return service.createDocumentTemplate(principal(authentication), request);
    }

    @GetMapping("/document-templates/{templateId}/revisions")
    public List<DocumentTemplateRevisionResponse> documentTemplateRevisions(
            Authentication authentication,
            @PathVariable Long templateId
    ) {
        return service.listDocumentTemplateRevisions(principal(authentication), templateId);
    }

    @PostMapping("/document-templates/{templateId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentTemplateRevisionResponse createDocumentTemplateRevision(
            Authentication authentication,
            @PathVariable Long templateId,
            @Valid @RequestBody CreateDocumentTemplateRevisionRequest request
    ) {
        return service.createDocumentTemplateRevision(principal(authentication), templateId, request);
    }

    @PostMapping("/document-template-revisions/{revisionId}/publish")
    public DocumentTemplateRevisionResponse publishDocumentTemplateRevision(
            Authentication authentication,
            @PathVariable Long revisionId
    ) {
        return service.publishDocumentTemplateRevision(principal(authentication), revisionId);
    }

    @PutMapping(
            value = "/document-template-revisions/{revisionId}/content",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentTemplateRevisionResponse uploadDocumentTemplateRevisionContent(
            Authentication authentication,
            @PathVariable Long revisionId,
            @RequestParam("file") MultipartFile file
    ) {
        return service.uploadDocumentTemplateRevisionContent(principal(authentication), revisionId, file);
    }

    @GetMapping("/document-template-revisions/{revisionId}/content")
    public ResponseEntity<byte[]> downloadDocumentTemplateRevisionContent(
            Authentication authentication,
            @PathVariable Long revisionId
    ) {
        var content = service.downloadDocumentTemplateRevisionContent(principal(authentication), revisionId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.content().length)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + content.filename() + "\"")
                .body(content.content());
    }

    @GetMapping("/workflow-definitions")
    public List<ConfigDefinitionResponse> workflowDefinitions(
            Authentication authentication,
            @RequestParam(required = false) String reportType
    ) {
        return service.listWorkflowDefinitions(principal(authentication), reportType);
    }

    @PostMapping("/workflow-definitions")
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigDefinitionResponse createWorkflowDefinition(
            Authentication authentication,
            @Valid @RequestBody CreateConfigDefinitionRequest request
    ) {
        return service.createWorkflowDefinition(principal(authentication), request);
    }

    @GetMapping("/workflow-definitions/{definitionId}/revisions")
    public List<JsonConfigRevisionResponse> workflowRevisions(
            Authentication authentication,
            @PathVariable Long definitionId
    ) {
        return service.listWorkflowRevisions(principal(authentication), definitionId);
    }

    @PostMapping("/workflow-definitions/{definitionId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public JsonConfigRevisionResponse createWorkflowRevision(
            Authentication authentication,
            @PathVariable Long definitionId,
            @Valid @RequestBody CreateJsonConfigRevisionRequest request
    ) {
        return service.createWorkflowRevision(principal(authentication), definitionId, request);
    }

    @PostMapping("/workflow-definition-revisions/{revisionId}/publish")
    public JsonConfigRevisionResponse publishWorkflowRevision(
            Authentication authentication,
            @PathVariable Long revisionId
    ) {
        return service.publishWorkflowRevision(principal(authentication), revisionId);
    }

    @GetMapping("/rule-sets")
    public List<ConfigDefinitionResponse> ruleSets(
            Authentication authentication,
            @RequestParam(required = false) String reportType
    ) {
        return service.listRuleSets(principal(authentication), reportType);
    }

    @PostMapping("/rule-sets")
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigDefinitionResponse createRuleSet(
            Authentication authentication,
            @Valid @RequestBody CreateConfigDefinitionRequest request
    ) {
        return service.createRuleSet(principal(authentication), request);
    }

    @GetMapping("/rule-sets/{ruleSetId}/revisions")
    public List<JsonConfigRevisionResponse> ruleSetRevisions(
            Authentication authentication,
            @PathVariable Long ruleSetId
    ) {
        return service.listRuleSetRevisions(principal(authentication), ruleSetId);
    }

    @PostMapping("/rule-sets/{ruleSetId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public JsonConfigRevisionResponse createRuleSetRevision(
            Authentication authentication,
            @PathVariable Long ruleSetId,
            @Valid @RequestBody CreateJsonConfigRevisionRequest request
    ) {
        return service.createRuleSetRevision(principal(authentication), ruleSetId, request);
    }

    @PostMapping("/rule-set-revisions/{revisionId}/publish")
    public JsonConfigRevisionResponse publishRuleSetRevision(
            Authentication authentication,
            @PathVariable Long revisionId
    ) {
        return service.publishRuleSetRevision(principal(authentication), revisionId);
    }

    @GetMapping("/output-layouts")
    public List<ConfigDefinitionResponse> outputLayouts(
            Authentication authentication,
            @RequestParam(required = false) String reportType
    ) {
        return service.listOutputLayouts(principal(authentication), reportType);
    }

    @PostMapping("/output-layouts")
    @ResponseStatus(HttpStatus.CREATED)
    public ConfigDefinitionResponse createOutputLayout(
            Authentication authentication,
            @Valid @RequestBody CreateConfigDefinitionRequest request
    ) {
        return service.createOutputLayout(principal(authentication), request);
    }

    @GetMapping("/output-layouts/{configId}/revisions")
    public List<JsonConfigRevisionResponse> outputLayoutRevisions(
            Authentication authentication,
            @PathVariable Long configId
    ) {
        return service.listOutputLayoutRevisions(principal(authentication), configId);
    }

    @PostMapping("/output-layouts/{configId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public JsonConfigRevisionResponse createOutputLayoutRevision(
            Authentication authentication,
            @PathVariable Long configId,
            @Valid @RequestBody CreateJsonConfigRevisionRequest request
    ) {
        return service.createOutputLayoutRevision(principal(authentication), configId, request);
    }

    @PostMapping("/output-layout-revisions/{revisionId}/publish")
    public JsonConfigRevisionResponse publishOutputLayoutRevision(
            Authentication authentication,
            @PathVariable Long revisionId
    ) {
        return service.publishOutputLayoutRevision(principal(authentication), revisionId);
    }

    @GetMapping("/office-overrides")
    public List<OfficeConfigOverrideResponse> officeOverrides(Authentication authentication) {
        return service.listOfficeOverrides(principal(authentication));
    }

    @PutMapping("/office-overrides/{reportType}")
    public OfficeConfigOverrideResponse updateOfficeOverride(
            Authentication authentication,
            @PathVariable String reportType,
            @RequestBody UpdateOfficeConfigOverrideRequest request
    ) {
        return service.updateOfficeOverride(principal(authentication), reportType, request);
    }

    @GetMapping("/resolve")
    public ResolvedOfficeConfigurationResponse resolve(
            Authentication authentication,
            @RequestParam String reportType
    ) {
        return service.resolve(principal(authentication), reportType);
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }
}
