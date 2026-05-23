package com.archdox.cloud.configuration.application;

import com.archdox.cloud.configuration.domain.ConfigResolutionSource;
import com.archdox.cloud.configuration.domain.ConfigRevisionStatus;
import com.archdox.cloud.configuration.domain.DocumentTemplate;
import com.archdox.cloud.configuration.domain.DocumentTemplateRevision;
import com.archdox.cloud.configuration.domain.OfficeConfigOverride;
import com.archdox.cloud.configuration.domain.OfficeConfigOverrideStatus;
import com.archdox.cloud.configuration.domain.OutputLayoutConfig;
import com.archdox.cloud.configuration.domain.OutputLayoutConfigRevision;
import com.archdox.cloud.configuration.domain.RuleSet;
import com.archdox.cloud.configuration.domain.RuleSetRevision;
import com.archdox.cloud.configuration.domain.WorkflowDefinition;
import com.archdox.cloud.configuration.domain.WorkflowDefinitionRevision;
import com.archdox.cloud.configuration.dto.ConfigDefinitionResponse;
import com.archdox.cloud.configuration.dto.CreateConfigDefinitionRequest;
import com.archdox.cloud.configuration.dto.CreateDocumentTemplateRevisionRequest;
import com.archdox.cloud.configuration.dto.CreateJsonConfigRevisionRequest;
import com.archdox.cloud.configuration.dto.DocumentTemplateRevisionResponse;
import com.archdox.cloud.configuration.dto.JsonConfigRevisionResponse;
import com.archdox.cloud.configuration.dto.OfficeConfigOverrideResponse;
import com.archdox.cloud.configuration.dto.ResolvedConfigPartResponse;
import com.archdox.cloud.configuration.dto.ResolvedOfficeConfigurationResponse;
import com.archdox.cloud.configuration.dto.UpdateOfficeConfigOverrideRequest;
import com.archdox.cloud.configuration.infra.DocumentTemplateRepository;
import com.archdox.cloud.configuration.infra.DocumentTemplateRevisionRepository;
import com.archdox.cloud.configuration.infra.OfficeConfigOverrideRepository;
import com.archdox.cloud.configuration.infra.OutputLayoutConfigRepository;
import com.archdox.cloud.configuration.infra.OutputLayoutConfigRevisionRepository;
import com.archdox.cloud.configuration.infra.RuleSetRepository;
import com.archdox.cloud.configuration.infra.RuleSetRevisionRepository;
import com.archdox.cloud.configuration.infra.WorkflowDefinitionRepository;
import com.archdox.cloud.configuration.infra.WorkflowDefinitionRevisionRepository;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.ConflictException;
import com.archdox.cloud.global.api.ForbiddenException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import com.archdox.cloud.document.infra.DocumentLocalObjectStore;
import com.archdox.cloud.office.application.OfficeContext;
import com.archdox.cloud.office.infra.OfficeMembershipRepository;
import com.archdox.shared.MembershipRole;
import com.archdox.shared.MembershipStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ConfigurationRegistryService {
    private static final String TEMPLATE_STORAGE_KIND_API_LOCAL = "API_LOCAL";
    private static final String DOCX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final OfficeMembershipRepository membershipRepository;
    private final DocumentTemplateRepository templateRepository;
    private final DocumentTemplateRevisionRepository templateRevisionRepository;
    private final WorkflowDefinitionRepository workflowRepository;
    private final WorkflowDefinitionRevisionRepository workflowRevisionRepository;
    private final RuleSetRepository ruleSetRepository;
    private final RuleSetRevisionRepository ruleSetRevisionRepository;
    private final OutputLayoutConfigRepository layoutRepository;
    private final OutputLayoutConfigRevisionRepository layoutRevisionRepository;
    private final OfficeConfigOverrideRepository overrideRepository;
    private final DocumentLocalObjectStore objectStore;

    public ConfigurationRegistryService(
            OfficeMembershipRepository membershipRepository,
            DocumentTemplateRepository templateRepository,
            DocumentTemplateRevisionRepository templateRevisionRepository,
            WorkflowDefinitionRepository workflowRepository,
            WorkflowDefinitionRevisionRepository workflowRevisionRepository,
            RuleSetRepository ruleSetRepository,
            RuleSetRevisionRepository ruleSetRevisionRepository,
            OutputLayoutConfigRepository layoutRepository,
            OutputLayoutConfigRevisionRepository layoutRevisionRepository,
            OfficeConfigOverrideRepository overrideRepository,
            DocumentLocalObjectStore objectStore
    ) {
        this.membershipRepository = membershipRepository;
        this.templateRepository = templateRepository;
        this.templateRevisionRepository = templateRevisionRepository;
        this.workflowRepository = workflowRepository;
        this.workflowRevisionRepository = workflowRevisionRepository;
        this.ruleSetRepository = ruleSetRepository;
        this.ruleSetRevisionRepository = ruleSetRevisionRepository;
        this.layoutRepository = layoutRepository;
        this.layoutRevisionRepository = layoutRevisionRepository;
        this.overrideRepository = overrideRepository;
        this.objectStore = objectStore;
    }

    @Transactional(readOnly = true)
    public List<ConfigDefinitionResponse> listDocumentTemplates(UserPrincipal principal, String reportType) {
        var officeId = requireOfficeAdmin(principal);
        return templateRepository.findVisible(officeId, normalizeOptionalCode(reportType)).stream()
                .map(template -> new ConfigDefinitionResponse(
                        template.id(),
                        template.officeId(),
                        template.templateCode(),
                        template.name(),
                        template.reportType(),
                        template.status(),
                        template.createdBy(),
                        template.createdAt(),
                        template.updatedAt()))
                .toList();
    }

    @Transactional
    public ConfigDefinitionResponse createDocumentTemplate(
            UserPrincipal principal,
            CreateConfigDefinitionRequest request
    ) {
        var officeId = requireOfficeAdmin(principal);
        var now = OffsetDateTime.now();
        var template = templateRepository.save(new DocumentTemplate(
                officeId,
                normalizeRequiredCode(request.code(), "code"),
                required(request.name(), "name"),
                normalizeOptionalCode(request.reportType()),
                principal.userId(),
                now));
        return new ConfigDefinitionResponse(
                template.id(),
                template.officeId(),
                template.templateCode(),
                template.name(),
                template.reportType(),
                template.status(),
                template.createdBy(),
                template.createdAt(),
                template.updatedAt());
    }

    @Transactional(readOnly = true)
    public List<DocumentTemplateRevisionResponse> listDocumentTemplateRevisions(
            UserPrincipal principal,
            Long templateId
    ) {
        var officeId = requireOfficeAdmin(principal);
        var template = requireVisibleTemplate(templateId, officeId);
        return templateRevisionRepository.findByTemplateIdOrderByVersionDesc(template.id()).stream()
                .map(this::toTemplateRevisionResponse)
                .toList();
    }

    @Transactional
    public DocumentTemplateRevisionResponse createDocumentTemplateRevision(
            UserPrincipal principal,
            Long templateId,
            CreateDocumentTemplateRevisionRequest request
    ) {
        var officeId = requireOfficeAdmin(principal);
        var template = requireOfficeOwnedTemplate(templateId, officeId);
        var version = templateRevisionRepository.maxVersion(template.id()) + 1;
        var revision = templateRevisionRepository.save(new DocumentTemplateRevision(
                template,
                version,
                trimToNull(request.templateStorageKind()),
                trimToNull(request.templateStorageRef()),
                safeMap(request.schema()),
                safeMap(request.composePolicy()),
                safeMap(request.aiPrompts()),
                principal.userId(),
                OffsetDateTime.now()));
        return toTemplateRevisionResponse(revision);
    }

    @Transactional
    public DocumentTemplateRevisionResponse publishDocumentTemplateRevision(
            UserPrincipal principal,
            Long revisionId
    ) {
        var officeId = requireOfficeAdmin(principal);
        var revision = templateRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new NotFoundException("Document template revision not found"));
        requireOfficeOwned(revision.template().officeId(), officeId, "Document template revision not found");
        revision.publish(principal.userId(), OffsetDateTime.now());
        return toTemplateRevisionResponse(revision);
    }

    @Transactional
    public DocumentTemplateRevisionResponse uploadDocumentTemplateRevisionContent(
            UserPrincipal principal,
            Long revisionId,
            MultipartFile file
    ) {
        var officeId = requireOfficeAdmin(principal);
        var revision = templateRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new NotFoundException("Document template revision not found"));
        requireOfficeOwned(revision.template().officeId(), officeId, "Document template revision not found");
        if (revision.status() != ConfigRevisionStatus.DRAFT) {
            throw new ConflictException("Published document template revision content is immutable");
        }
        var filename = requireDocxFilename(file);
        var storageRef = "templates/offices/%d/document-templates/%d/revisions/%d/%s".formatted(
                officeId,
                revision.template().id(),
                revision.id(),
                filename);
        try (var input = file.getInputStream()) {
            objectStore.write(storageRef, input);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to store document template content", ex);
        }
        revision.attachTemplateContent(TEMPLATE_STORAGE_KIND_API_LOCAL, storageRef);
        return toTemplateRevisionResponse(revision);
    }

    @Transactional(readOnly = true)
    public DocumentTemplateContent downloadDocumentTemplateRevisionContent(
            UserPrincipal principal,
            Long revisionId
    ) {
        var officeId = requireOfficeAdmin(principal);
        var revision = templateRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new NotFoundException("Document template revision not found"));
        requireVisible(revision.template().officeId(), officeId, "Document template revision not found");
        if (!TEMPLATE_STORAGE_KIND_API_LOCAL.equals(revision.templateStorageKind())
                || trimToNull(revision.templateStorageRef()) == null
                || !objectStore.exists(revision.templateStorageRef())) {
            throw new NotFoundException("Document template revision content not found");
        }
        try (var input = objectStore.open(revision.templateStorageRef())) {
            return new DocumentTemplateContent(
                    filenameFromStorageRef(revision.templateStorageRef()),
                    DOCX_CONTENT_TYPE,
                    input.readAllBytes());
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read document template content", ex);
        }
    }

    @Transactional(readOnly = true)
    public List<ConfigDefinitionResponse> listWorkflowDefinitions(UserPrincipal principal, String reportType) {
        var officeId = requireOfficeAdmin(principal);
        return workflowRepository.findVisible(officeId, normalizeOptionalCode(reportType)).stream()
                .map(this::toWorkflowDefinitionResponse)
                .toList();
    }

    @Transactional
    public ConfigDefinitionResponse createWorkflowDefinition(UserPrincipal principal, CreateConfigDefinitionRequest request) {
        var officeId = requireOfficeAdmin(principal);
        var now = OffsetDateTime.now();
        return toWorkflowDefinitionResponse(workflowRepository.save(new WorkflowDefinition(
                officeId,
                normalizeRequiredCode(request.code(), "code"),
                required(request.name(), "name"),
                normalizeOptionalCode(request.reportType()),
                principal.userId(),
                now)));
    }

    @Transactional(readOnly = true)
    public List<JsonConfigRevisionResponse> listWorkflowRevisions(UserPrincipal principal, Long definitionId) {
        var officeId = requireOfficeAdmin(principal);
        var definition = requireVisibleWorkflow(definitionId, officeId);
        return workflowRevisionRepository.findByDefinitionIdOrderByVersionDesc(definition.id()).stream()
                .map(this::toWorkflowRevisionResponse)
                .toList();
    }

    @Transactional
    public JsonConfigRevisionResponse createWorkflowRevision(
            UserPrincipal principal,
            Long definitionId,
            CreateJsonConfigRevisionRequest request
    ) {
        var officeId = requireOfficeAdmin(principal);
        var definition = requireOfficeOwnedWorkflow(definitionId, officeId);
        var revision = workflowRevisionRepository.save(new WorkflowDefinitionRevision(
                definition,
                workflowRevisionRepository.maxVersion(definition.id()) + 1,
                safeMap(request.payload()),
                principal.userId(),
                OffsetDateTime.now()));
        return toWorkflowRevisionResponse(revision);
    }

    @Transactional
    public JsonConfigRevisionResponse publishWorkflowRevision(UserPrincipal principal, Long revisionId) {
        var officeId = requireOfficeAdmin(principal);
        var revision = workflowRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new NotFoundException("Workflow definition revision not found"));
        requireOfficeOwned(revision.definition().officeId(), officeId, "Workflow definition revision not found");
        revision.publish(principal.userId(), OffsetDateTime.now());
        return toWorkflowRevisionResponse(revision);
    }

    @Transactional(readOnly = true)
    public List<ConfigDefinitionResponse> listRuleSets(UserPrincipal principal, String reportType) {
        var officeId = requireOfficeAdmin(principal);
        return ruleSetRepository.findVisible(officeId, normalizeOptionalCode(reportType)).stream()
                .map(this::toRuleSetResponse)
                .toList();
    }

    @Transactional
    public ConfigDefinitionResponse createRuleSet(UserPrincipal principal, CreateConfigDefinitionRequest request) {
        var officeId = requireOfficeAdmin(principal);
        var now = OffsetDateTime.now();
        return toRuleSetResponse(ruleSetRepository.save(new RuleSet(
                officeId,
                normalizeRequiredCode(request.code(), "code"),
                required(request.name(), "name"),
                normalizeOptionalCode(request.reportType()),
                principal.userId(),
                now)));
    }

    @Transactional(readOnly = true)
    public List<JsonConfigRevisionResponse> listRuleSetRevisions(UserPrincipal principal, Long ruleSetId) {
        var officeId = requireOfficeAdmin(principal);
        var ruleSet = requireVisibleRuleSet(ruleSetId, officeId);
        return ruleSetRevisionRepository.findByRuleSetIdOrderByVersionDesc(ruleSet.id()).stream()
                .map(this::toRuleSetRevisionResponse)
                .toList();
    }

    @Transactional
    public JsonConfigRevisionResponse createRuleSetRevision(
            UserPrincipal principal,
            Long ruleSetId,
            CreateJsonConfigRevisionRequest request
    ) {
        var officeId = requireOfficeAdmin(principal);
        var ruleSet = requireOfficeOwnedRuleSet(ruleSetId, officeId);
        var revision = ruleSetRevisionRepository.save(new RuleSetRevision(
                ruleSet,
                ruleSetRevisionRepository.maxVersion(ruleSet.id()) + 1,
                safeMap(request.payload()),
                principal.userId(),
                OffsetDateTime.now()));
        return toRuleSetRevisionResponse(revision);
    }

    @Transactional
    public JsonConfigRevisionResponse publishRuleSetRevision(UserPrincipal principal, Long revisionId) {
        var officeId = requireOfficeAdmin(principal);
        var revision = ruleSetRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new NotFoundException("Rule set revision not found"));
        requireOfficeOwned(revision.ruleSet().officeId(), officeId, "Rule set revision not found");
        revision.publish(principal.userId(), OffsetDateTime.now());
        return toRuleSetRevisionResponse(revision);
    }

    @Transactional(readOnly = true)
    public List<ConfigDefinitionResponse> listOutputLayouts(UserPrincipal principal, String reportType) {
        var officeId = requireOfficeAdmin(principal);
        return layoutRepository.findVisible(officeId, normalizeOptionalCode(reportType)).stream()
                .map(this::toLayoutResponse)
                .toList();
    }

    @Transactional
    public ConfigDefinitionResponse createOutputLayout(UserPrincipal principal, CreateConfigDefinitionRequest request) {
        var officeId = requireOfficeAdmin(principal);
        var now = OffsetDateTime.now();
        return toLayoutResponse(layoutRepository.save(new OutputLayoutConfig(
                officeId,
                normalizeRequiredCode(request.code(), "code"),
                required(request.name(), "name"),
                normalizeOptionalCode(request.reportType()),
                principal.userId(),
                now)));
    }

    @Transactional(readOnly = true)
    public List<JsonConfigRevisionResponse> listOutputLayoutRevisions(UserPrincipal principal, Long configId) {
        var officeId = requireOfficeAdmin(principal);
        var config = requireVisibleLayout(configId, officeId);
        return layoutRevisionRepository.findByConfigIdOrderByVersionDesc(config.id()).stream()
                .map(this::toLayoutRevisionResponse)
                .toList();
    }

    @Transactional
    public JsonConfigRevisionResponse createOutputLayoutRevision(
            UserPrincipal principal,
            Long configId,
            CreateJsonConfigRevisionRequest request
    ) {
        var officeId = requireOfficeAdmin(principal);
        var config = requireOfficeOwnedLayout(configId, officeId);
        var revision = layoutRevisionRepository.save(new OutputLayoutConfigRevision(
                config,
                layoutRevisionRepository.maxVersion(config.id()) + 1,
                safeMap(request.payload()),
                principal.userId(),
                OffsetDateTime.now()));
        return toLayoutRevisionResponse(revision);
    }

    @Transactional
    public JsonConfigRevisionResponse publishOutputLayoutRevision(UserPrincipal principal, Long revisionId) {
        var officeId = requireOfficeAdmin(principal);
        var revision = layoutRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new NotFoundException("Output layout revision not found"));
        requireOfficeOwned(revision.config().officeId(), officeId, "Output layout revision not found");
        revision.publish(principal.userId(), OffsetDateTime.now());
        return toLayoutRevisionResponse(revision);
    }

    @Transactional(readOnly = true)
    public List<OfficeConfigOverrideResponse> listOfficeOverrides(UserPrincipal principal) {
        var officeId = requireOfficeAdmin(principal);
        return overrideRepository.findByOfficeIdOrderByUpdatedAtDesc(officeId).stream()
                .map(this::toOverrideResponse)
                .toList();
    }

    @Transactional
    public OfficeConfigOverrideResponse updateOfficeOverride(
            UserPrincipal principal,
            String reportType,
            UpdateOfficeConfigOverrideRequest request
    ) {
        var officeId = requireOfficeAdmin(principal);
        var normalizedReportType = normalizeRequiredCode(reportType, "reportType");
        var now = OffsetDateTime.now();
        var override = overrideRepository.findByOfficeIdAndReportTypeAndStatus(
                        officeId,
                        normalizedReportType,
                        OfficeConfigOverrideStatus.ACTIVE)
                .orElseGet(() -> new OfficeConfigOverride(officeId, normalizedReportType, principal.userId(), now));
        override.update(
                requirePublishedTemplateRevision(officeId, request.templateRevisionId()),
                requirePublishedWorkflowRevision(officeId, request.workflowRevisionId()),
                requirePublishedRuleSetRevision(officeId, request.ruleSetRevisionId()),
                requirePublishedLayoutRevision(officeId, request.outputLayoutRevisionId()),
                request.effectiveFrom(),
                request.effectiveTo(),
                principal.userId(),
                now);
        return toOverrideResponse(overrideRepository.save(override));
    }

    @Transactional(readOnly = true)
    public ResolvedOfficeConfigurationResponse resolve(UserPrincipal principal, String reportType) {
        var officeId = requireOfficeAdmin(principal);
        return toResolveResponse(resolveForDocumentGeneration(officeId, reportType));
    }

    @Transactional(readOnly = true)
    public ResolvedDocumentConfiguration resolveForDocumentGeneration(Long officeId, String reportType) {
        var normalizedReportType = normalizeRequiredCode(reportType, "reportType");
        var override = overrideRepository.findByOfficeIdAndReportTypeAndStatus(
                        officeId,
                        normalizedReportType,
                        OfficeConfigOverrideStatus.ACTIVE)
                .orElse(null);
        return new ResolvedDocumentConfiguration(
                officeId,
                normalizedReportType,
                override != null && override.templateRevision() != null
                        ? toDocumentTemplatePart(override.templateRevision(), ConfigResolutionSource.OFFICE_OVERRIDE)
                        : resolveSystemTemplate(normalizedReportType),
                override != null && override.workflowRevision() != null
                        ? toDocumentWorkflowPart(override.workflowRevision(), ConfigResolutionSource.OFFICE_OVERRIDE)
                        : resolveSystemWorkflow(normalizedReportType),
                override != null && override.ruleSetRevision() != null
                        ? toDocumentRuleSetPart(override.ruleSetRevision(), ConfigResolutionSource.OFFICE_OVERRIDE)
                        : resolveSystemRuleSet(normalizedReportType),
                override != null && override.outputLayoutRevision() != null
                        ? toDocumentLayoutPart(override.outputLayoutRevision(), ConfigResolutionSource.OFFICE_OVERRIDE)
                        : resolveSystemLayout(normalizedReportType));
    }

    private Long requireOfficeAdmin(UserPrincipal principal) {
        var officeId = OfficeContext.requireCurrentOfficeId();
        var membership = membershipRepository.findByUserIdAndOfficeIdAndStatus(
                        principal.userId(),
                        officeId,
                        MembershipStatus.ACTIVE)
                .orElseThrow(() -> new ForbiddenException("Office membership required"));
        if (membership.role() != MembershipRole.OWNER && membership.role() != MembershipRole.ADMIN) {
            throw new ForbiddenException("Office admin role required");
        }
        return officeId;
    }

    private DocumentTemplate requireVisibleTemplate(Long templateId, Long officeId) {
        var template = templateRepository.findById(templateId)
                .orElseThrow(() -> new NotFoundException("Document template not found"));
        requireVisible(template.officeId(), officeId, "Document template not found");
        return template;
    }

    private DocumentTemplate requireOfficeOwnedTemplate(Long templateId, Long officeId) {
        var template = requireVisibleTemplate(templateId, officeId);
        requireOfficeOwned(template.officeId(), officeId, "Document template not found");
        return template;
    }

    private WorkflowDefinition requireVisibleWorkflow(Long definitionId, Long officeId) {
        var definition = workflowRepository.findById(definitionId)
                .orElseThrow(() -> new NotFoundException("Workflow definition not found"));
        requireVisible(definition.officeId(), officeId, "Workflow definition not found");
        return definition;
    }

    private WorkflowDefinition requireOfficeOwnedWorkflow(Long definitionId, Long officeId) {
        var definition = requireVisibleWorkflow(definitionId, officeId);
        requireOfficeOwned(definition.officeId(), officeId, "Workflow definition not found");
        return definition;
    }

    private RuleSet requireVisibleRuleSet(Long ruleSetId, Long officeId) {
        var ruleSet = ruleSetRepository.findById(ruleSetId)
                .orElseThrow(() -> new NotFoundException("Rule set not found"));
        requireVisible(ruleSet.officeId(), officeId, "Rule set not found");
        return ruleSet;
    }

    private RuleSet requireOfficeOwnedRuleSet(Long ruleSetId, Long officeId) {
        var ruleSet = requireVisibleRuleSet(ruleSetId, officeId);
        requireOfficeOwned(ruleSet.officeId(), officeId, "Rule set not found");
        return ruleSet;
    }

    private OutputLayoutConfig requireVisibleLayout(Long configId, Long officeId) {
        var config = layoutRepository.findById(configId)
                .orElseThrow(() -> new NotFoundException("Output layout config not found"));
        requireVisible(config.officeId(), officeId, "Output layout config not found");
        return config;
    }

    private OutputLayoutConfig requireOfficeOwnedLayout(Long configId, Long officeId) {
        var config = requireVisibleLayout(configId, officeId);
        requireOfficeOwned(config.officeId(), officeId, "Output layout config not found");
        return config;
    }

    private DocumentTemplateRevision requirePublishedTemplateRevision(Long officeId, Long revisionId) {
        if (revisionId == null) {
            return null;
        }
        var revision = templateRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new NotFoundException("Document template revision not found"));
        requireVisible(revision.template().officeId(), officeId, "Document template revision not found");
        requirePublished(revision.status(), "Document template revision must be published before override assignment");
        return revision;
    }

    private WorkflowDefinitionRevision requirePublishedWorkflowRevision(Long officeId, Long revisionId) {
        if (revisionId == null) {
            return null;
        }
        var revision = workflowRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new NotFoundException("Workflow definition revision not found"));
        requireVisible(revision.definition().officeId(), officeId, "Workflow definition revision not found");
        requirePublished(revision.status(), "Workflow definition revision must be published before override assignment");
        return revision;
    }

    private RuleSetRevision requirePublishedRuleSetRevision(Long officeId, Long revisionId) {
        if (revisionId == null) {
            return null;
        }
        var revision = ruleSetRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new NotFoundException("Rule set revision not found"));
        requireVisible(revision.ruleSet().officeId(), officeId, "Rule set revision not found");
        requirePublished(revision.status(), "Rule set revision must be published before override assignment");
        return revision;
    }

    private OutputLayoutConfigRevision requirePublishedLayoutRevision(Long officeId, Long revisionId) {
        if (revisionId == null) {
            return null;
        }
        var revision = layoutRevisionRepository.findById(revisionId)
                .orElseThrow(() -> new NotFoundException("Output layout revision not found"));
        requireVisible(revision.config().officeId(), officeId, "Output layout revision not found");
        requirePublished(revision.status(), "Output layout revision must be published before override assignment");
        return revision;
    }

    private void requireVisible(Long rowOfficeId, Long currentOfficeId, String message) {
        if (rowOfficeId != null && !Objects.equals(rowOfficeId, currentOfficeId)) {
            throw new NotFoundException(message);
        }
    }

    private void requireOfficeOwned(Long rowOfficeId, Long currentOfficeId, String message) {
        if (!Objects.equals(rowOfficeId, currentOfficeId)) {
            throw new NotFoundException(message);
        }
    }

    private void requirePublished(ConfigRevisionStatus status, String message) {
        if (status != ConfigRevisionStatus.PUBLISHED) {
            throw new BadRequestException(message);
        }
    }

    private ResolvedDocumentTemplateConfig resolveSystemTemplate(String reportType) {
        return templateRevisionRepository.findSystemPublishedCandidates(
                        reportType,
                        ConfigRevisionStatus.PUBLISHED,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(revision -> toDocumentTemplatePart(revision, ConfigResolutionSource.SYSTEM_DEFAULT))
                .orElseGet(ResolvedDocumentTemplateConfig::notConfigured);
    }

    private ResolvedDocumentConfigPart resolveSystemWorkflow(String reportType) {
        return workflowRevisionRepository.findSystemPublishedCandidates(
                        reportType,
                        ConfigRevisionStatus.PUBLISHED,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(revision -> toDocumentWorkflowPart(revision, ConfigResolutionSource.SYSTEM_DEFAULT))
                .orElseGet(ResolvedDocumentConfigPart::notConfigured);
    }

    private ResolvedDocumentConfigPart resolveSystemRuleSet(String reportType) {
        return ruleSetRevisionRepository.findSystemPublishedCandidates(
                        reportType,
                        ConfigRevisionStatus.PUBLISHED,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(revision -> toDocumentRuleSetPart(revision, ConfigResolutionSource.SYSTEM_DEFAULT))
                .orElseGet(ResolvedDocumentConfigPart::notConfigured);
    }

    private ResolvedDocumentConfigPart resolveSystemLayout(String reportType) {
        return layoutRevisionRepository.findSystemPublishedCandidates(
                        reportType,
                        ConfigRevisionStatus.PUBLISHED,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(revision -> toDocumentLayoutPart(revision, ConfigResolutionSource.SYSTEM_DEFAULT))
                .orElseGet(ResolvedDocumentConfigPart::notConfigured);
    }

    private ConfigDefinitionResponse toWorkflowDefinitionResponse(WorkflowDefinition definition) {
        return new ConfigDefinitionResponse(
                definition.id(),
                definition.officeId(),
                definition.workflowCode(),
                definition.name(),
                definition.reportType(),
                definition.status(),
                definition.createdBy(),
                definition.createdAt(),
                definition.updatedAt());
    }

    private ConfigDefinitionResponse toRuleSetResponse(RuleSet ruleSet) {
        return new ConfigDefinitionResponse(
                ruleSet.id(),
                ruleSet.officeId(),
                ruleSet.ruleSetCode(),
                ruleSet.name(),
                ruleSet.reportType(),
                ruleSet.status(),
                ruleSet.createdBy(),
                ruleSet.createdAt(),
                ruleSet.updatedAt());
    }

    private ConfigDefinitionResponse toLayoutResponse(OutputLayoutConfig config) {
        return new ConfigDefinitionResponse(
                config.id(),
                config.officeId(),
                config.layoutCode(),
                config.name(),
                config.reportType(),
                config.status(),
                config.createdBy(),
                config.createdAt(),
                config.updatedAt());
    }

    private DocumentTemplateRevisionResponse toTemplateRevisionResponse(DocumentTemplateRevision revision) {
        return new DocumentTemplateRevisionResponse(
                revision.id(),
                revision.template().id(),
                revision.version(),
                revision.status(),
                revision.templateStorageKind(),
                revision.templateStorageRef(),
                revision.schemaJson(),
                revision.composePolicyJson(),
                revision.aiPromptsJson(),
                revision.createdBy(),
                revision.publishedBy(),
                revision.createdAt(),
                revision.publishedAt());
    }

    private JsonConfigRevisionResponse toWorkflowRevisionResponse(WorkflowDefinitionRevision revision) {
        return new JsonConfigRevisionResponse(
                revision.id(),
                revision.definition().id(),
                revision.version(),
                revision.status(),
                revision.definitionJson(),
                revision.createdBy(),
                revision.publishedBy(),
                revision.createdAt(),
                revision.publishedAt());
    }

    private JsonConfigRevisionResponse toRuleSetRevisionResponse(RuleSetRevision revision) {
        return new JsonConfigRevisionResponse(
                revision.id(),
                revision.ruleSet().id(),
                revision.version(),
                revision.status(),
                revision.rulesJson(),
                revision.createdBy(),
                revision.publishedBy(),
                revision.createdAt(),
                revision.publishedAt());
    }

    private JsonConfigRevisionResponse toLayoutRevisionResponse(OutputLayoutConfigRevision revision) {
        return new JsonConfigRevisionResponse(
                revision.id(),
                revision.config().id(),
                revision.version(),
                revision.status(),
                revision.layoutJson(),
                revision.createdBy(),
                revision.publishedBy(),
                revision.createdAt(),
                revision.publishedAt());
    }

    private OfficeConfigOverrideResponse toOverrideResponse(OfficeConfigOverride override) {
        return new OfficeConfigOverrideResponse(
                override.id(),
                override.officeId(),
                override.reportType(),
                override.status(),
                override.templateRevision() == null
                        ? ResolvedConfigPartResponse.notConfigured()
                        : toTemplatePart(override.templateRevision(), ConfigResolutionSource.OFFICE_OVERRIDE),
                override.workflowRevision() == null
                        ? ResolvedConfigPartResponse.notConfigured()
                        : toWorkflowPart(override.workflowRevision(), ConfigResolutionSource.OFFICE_OVERRIDE),
                override.ruleSetRevision() == null
                        ? ResolvedConfigPartResponse.notConfigured()
                        : toRuleSetPart(override.ruleSetRevision(), ConfigResolutionSource.OFFICE_OVERRIDE),
                override.outputLayoutRevision() == null
                        ? ResolvedConfigPartResponse.notConfigured()
                        : toLayoutPart(override.outputLayoutRevision(), ConfigResolutionSource.OFFICE_OVERRIDE),
                override.effectiveFrom(),
                override.effectiveTo(),
                override.createdBy(),
                override.updatedBy(),
                override.createdAt(),
                override.updatedAt());
    }

    private ResolvedConfigPartResponse toTemplatePart(DocumentTemplateRevision revision, ConfigResolutionSource source) {
        var part = toDocumentTemplatePart(revision, source);
        return toResponsePart(part);
    }

    private ResolvedDocumentTemplateConfig toDocumentTemplatePart(DocumentTemplateRevision revision, ConfigResolutionSource source) {
        var template = revision.template();
        return new ResolvedDocumentTemplateConfig(
                source,
                template.id(),
                revision.id(),
                template.templateCode(),
                template.name(),
                template.reportType(),
                revision.version(),
                revision.templateStorageKind(),
                revision.templateStorageRef(),
                revision.schemaJson(),
                revision.composePolicyJson(),
                revision.aiPromptsJson());
    }

    private ResolvedConfigPartResponse toWorkflowPart(WorkflowDefinitionRevision revision, ConfigResolutionSource source) {
        return toResponsePart(toDocumentWorkflowPart(revision, source));
    }

    private ResolvedDocumentConfigPart toDocumentWorkflowPart(WorkflowDefinitionRevision revision, ConfigResolutionSource source) {
        var definition = revision.definition();
        return new ResolvedDocumentConfigPart(
                source,
                definition.id(),
                revision.id(),
                definition.workflowCode(),
                definition.name(),
                definition.reportType(),
                revision.version(),
                revision.definitionJson());
    }

    private ResolvedConfigPartResponse toRuleSetPart(RuleSetRevision revision, ConfigResolutionSource source) {
        return toResponsePart(toDocumentRuleSetPart(revision, source));
    }

    private ResolvedDocumentConfigPart toDocumentRuleSetPart(RuleSetRevision revision, ConfigResolutionSource source) {
        var ruleSet = revision.ruleSet();
        return new ResolvedDocumentConfigPart(
                source,
                ruleSet.id(),
                revision.id(),
                ruleSet.ruleSetCode(),
                ruleSet.name(),
                ruleSet.reportType(),
                revision.version(),
                revision.rulesJson());
    }

    private ResolvedConfigPartResponse toLayoutPart(OutputLayoutConfigRevision revision, ConfigResolutionSource source) {
        return toResponsePart(toDocumentLayoutPart(revision, source));
    }

    private ResolvedDocumentConfigPart toDocumentLayoutPart(OutputLayoutConfigRevision revision, ConfigResolutionSource source) {
        var config = revision.config();
        return new ResolvedDocumentConfigPart(
                source,
                config.id(),
                revision.id(),
                config.layoutCode(),
                config.name(),
                config.reportType(),
                revision.version(),
                revision.layoutJson());
    }

    private ResolvedOfficeConfigurationResponse toResolveResponse(ResolvedDocumentConfiguration resolution) {
        return new ResolvedOfficeConfigurationResponse(
                resolution.officeId(),
                resolution.reportType(),
                toResponsePart(resolution.template()),
                toResponsePart(resolution.workflow()),
                toResponsePart(resolution.ruleSet()),
                toResponsePart(resolution.outputLayout()));
    }

    private ResolvedConfigPartResponse toResponsePart(ResolvedDocumentTemplateConfig part) {
        return new ResolvedConfigPartResponse(
                part.source(),
                part.definitionId(),
                part.revisionId(),
                part.code(),
                part.name(),
                part.reportType(),
                part.version());
    }

    private ResolvedConfigPartResponse toResponsePart(ResolvedDocumentConfigPart part) {
        return new ResolvedConfigPartResponse(
                part.source(),
                part.definitionId(),
                part.revisionId(),
                part.code(),
                part.name(),
                part.reportType(),
                part.version());
    }

    private String normalizeRequiredCode(String value, String fieldName) {
        var normalized = normalizeOptionalCode(value);
        if (normalized == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeOptionalCode(String value) {
        var trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String required(String value, String fieldName) {
        var trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BadRequestException(fieldName + " is required");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String requireDocxFilename(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Template DOCX file is required");
        }
        var filename = trimToNull(file.getOriginalFilename());
        if (filename == null) {
            filename = "template.docx";
        }
        filename = filename.replace('\\', '/');
        var slashIndex = filename.lastIndexOf('/');
        if (slashIndex >= 0) {
            filename = filename.substring(slashIndex + 1);
        }
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new BadRequestException("Template file must be a .docx file");
        }
        var safeFilename = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeFilename.isBlank() || ".docx".equalsIgnoreCase(safeFilename)) {
            return "template.docx";
        }
        return safeFilename;
    }

    private String filenameFromStorageRef(String storageRef) {
        var normalized = storageRef.replace('\\', '/');
        var slashIndex = normalized.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex + 1 < normalized.length()) {
            return normalized.substring(slashIndex + 1);
        }
        return "template.docx";
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : Map.copyOf(value);
    }

    public record DocumentTemplateContent(
            String filename,
            String contentType,
            byte[] content
    ) {
    }
}
