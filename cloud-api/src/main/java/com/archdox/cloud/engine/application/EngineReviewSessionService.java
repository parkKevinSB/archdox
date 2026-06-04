package com.archdox.cloud.engine.application;

import com.archdox.cloud.engine.auth.application.EngineApiPrincipal;
import com.archdox.cloud.engine.context.ArchDoxContextAmbiguity;
import com.archdox.cloud.engine.context.ArchDoxContextFact;
import com.archdox.cloud.engine.context.ArchDoxContextFactSource;
import com.archdox.cloud.engine.context.ArchDoxContextNormalizationService;
import com.archdox.cloud.engine.context.ArchDoxMissingContextQuestion;
import com.archdox.cloud.engine.context.ArchDoxNormalizedContext;
import com.archdox.cloud.engine.domain.EngineReviewSession;
import com.archdox.cloud.engine.dto.CreateEngineReviewSessionRequest;
import com.archdox.cloud.engine.dto.EngineReviewResultResponse;
import com.archdox.cloud.engine.dto.EngineContextFactRequest;
import com.archdox.cloud.engine.dto.EngineReviewSessionResponse;
import com.archdox.cloud.engine.dto.SubmitEngineReviewDocumentRequest;
import com.archdox.cloud.engine.dto.SubmitEngineReviewFactsRequest;
import com.archdox.cloud.engine.infra.EngineReviewSessionRepository;
import com.archdox.cloud.global.api.BadRequestException;
import com.archdox.cloud.global.api.NotFoundException;
import com.archdox.cloud.global.security.UserPrincipal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EngineReviewSessionService {
    private final EngineReviewSessionRepository repository;
    private final ArchDoxContextNormalizationService normalizationService;
    private final EngineValidationService validationService;

    public EngineReviewSessionService(
            EngineReviewSessionRepository repository,
            ArchDoxContextNormalizationService normalizationService,
            EngineValidationService validationService
    ) {
        this.repository = repository;
        this.normalizationService = normalizationService;
        this.validationService = validationService;
    }

    @Transactional
    public EngineReviewSessionResponse create(
            CreateEngineReviewSessionRequest request,
            UserPrincipal principal
    ) {
        return create(request, principal, null);
    }

    @Transactional
    public EngineReviewSessionResponse create(
            CreateEngineReviewSessionRequest request,
            UserPrincipal principal,
            Long officeId
    ) {
        var now = OffsetDateTime.now();
        var session = repository.save(new EngineReviewSession(
                "rvw_sess_" + UUID.randomUUID(),
                principal.userId(),
                officeId,
                request.customerProjectRef(),
                request.reviewPurpose(),
                now));
        return toResponse(session);
    }

    @Transactional
    public EngineReviewSessionResponse create(
            CreateEngineReviewSessionRequest request,
            EngineApiPrincipal principal
    ) {
        return create(request, new UserPrincipal(principal.ownerUserId(), "engine-api:" + principal.keyId()), principal.officeId());
    }

    @Transactional
    public EngineReviewSessionResponse submitDocument(
            String reviewSessionId,
            SubmitEngineReviewDocumentRequest request,
            UserPrincipal principal
    ) {
        var session = session(reviewSessionId, principal);
        return submitDocument(session, request);
    }

    @Transactional
    public EngineReviewSessionResponse submitDocument(
            String reviewSessionId,
            SubmitEngineReviewDocumentRequest request,
            EngineApiPrincipal principal
    ) {
        var session = session(reviewSessionId, principal);
        return submitDocument(session, request);
    }

    private EngineReviewSessionResponse submitDocument(
            EngineReviewSession session,
            SubmitEngineReviewDocumentRequest request
    ) {
        session.submitDocument(request.documentTypeHint(), request.fileName(), request.contentText(), OffsetDateTime.now());
        return toResponse(session);
    }

    @Transactional
    public EngineReviewSessionResponse submitFacts(
            String reviewSessionId,
            SubmitEngineReviewFactsRequest request,
            UserPrincipal principal
    ) {
        var session = session(reviewSessionId, principal);
        return submitFacts(session, request);
    }

    @Transactional
    public EngineReviewSessionResponse submitFacts(
            String reviewSessionId,
            SubmitEngineReviewFactsRequest request,
            EngineApiPrincipal principal
    ) {
        var session = session(reviewSessionId, principal);
        return submitFacts(session, request);
    }

    private EngineReviewSessionResponse submitFacts(
            EngineReviewSession session,
            SubmitEngineReviewFactsRequest request
    ) {
        var facts = request.facts().stream()
                .map(this::factMap)
                .toList();
        session.submitFacts(facts, OffsetDateTime.now());
        return toResponse(session);
    }

    @Transactional
    public EngineReviewSessionResponse normalize(String reviewSessionId, UserPrincipal principal) {
        var session = session(reviewSessionId, principal);
        return normalize(session);
    }

    @Transactional
    public EngineReviewSessionResponse normalize(String reviewSessionId, EngineApiPrincipal principal) {
        var session = session(reviewSessionId, principal);
        return normalize(session);
    }

    private EngineReviewSessionResponse normalize(EngineReviewSession session) {
        var normalized = normalizationService.normalize(contextFacts(session));
        session.normalize(normalizedMap(normalized), OffsetDateTime.now());
        return toResponse(session);
    }

    @Transactional
    public EngineReviewSessionResponse runValidation(String reviewSessionId, UserPrincipal principal) {
        var session = session(reviewSessionId, principal);
        return runValidation(session);
    }

    @Transactional
    public EngineReviewSessionResponse runValidation(String reviewSessionId, EngineApiPrincipal principal) {
        var session = session(reviewSessionId, principal);
        return runValidation(session);
    }

    private EngineReviewSessionResponse runValidation(EngineReviewSession session) {
        if (isBlank(session.documentText()) && facts(session).isEmpty()) {
            throw new BadRequestException("Document text or context facts are required before validation");
        }
        if (session.normalizedContextJson().isEmpty()) {
            var normalized = normalizationService.normalize(contextFacts(session));
            session.normalize(normalizedMap(normalized), OffsetDateTime.now());
        }
        var validationResult = validationService.validate(session, session.normalizedContextJson());
        session.validate(validationResult.toJson(), OffsetDateTime.now());
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public EngineReviewSessionResponse get(String reviewSessionId, UserPrincipal principal) {
        return toResponse(session(reviewSessionId, principal));
    }

    @Transactional(readOnly = true)
    public EngineReviewSessionResponse get(String reviewSessionId, EngineApiPrincipal principal) {
        return toResponse(session(reviewSessionId, principal));
    }

    @Transactional(readOnly = true)
    public EngineReviewResultResponse getResult(String reviewSessionId, UserPrincipal principal) {
        return toResultResponse(session(reviewSessionId, principal));
    }

    @Transactional(readOnly = true)
    public EngineReviewResultResponse getResult(String reviewSessionId, EngineApiPrincipal principal) {
        return toResultResponse(session(reviewSessionId, principal));
    }

    private EngineReviewSession session(String reviewSessionId, UserPrincipal principal) {
        return repository.findByExternalSessionIdAndOwnerUserId(reviewSessionId, principal.userId())
                .orElseThrow(() -> new NotFoundException("Engine review session not found"));
    }

    private EngineReviewSession session(String reviewSessionId, EngineApiPrincipal principal) {
        if (principal.officeId() == null) {
            return repository.findByExternalSessionIdAndOwnerUserIdAndOfficeIdIsNull(
                            reviewSessionId,
                            principal.ownerUserId())
                    .orElseThrow(() -> new NotFoundException("Engine review session not found"));
        }
        return repository.findByExternalSessionIdAndOwnerUserIdAndOfficeId(
                        reviewSessionId,
                        principal.ownerUserId(),
                        principal.officeId())
                .orElseThrow(() -> new NotFoundException("Engine review session not found"));
    }

    private Map<String, Object> factMap(EngineContextFactRequest request) {
        var fieldName = request.resolvedFieldName();
        if (fieldName.isBlank()) {
            throw new BadRequestException("Fact field name is required");
        }
        if (isBlank(request.rawValue())) {
            throw new BadRequestException("Fact rawValue is required");
        }
        return Map.of(
                "fieldName", fieldName,
                "rawValue", request.rawValue().trim(),
                "source", source(request.source()).name(),
                "evidence", request.evidence() == null ? "" : request.evidence().trim(),
                "confidence", confidence(request.confidence()));
    }

    private List<ArchDoxContextFact> contextFacts(EngineReviewSession session) {
        return facts(session).stream()
                .map(fact -> new ArchDoxContextFact(
                        text(fact.get("fieldName")),
                        text(fact.get("rawValue")),
                        source(text(fact.get("source"))),
                        text(fact.get("evidence")),
                        confidence(fact.get("confidence"))))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> facts(EngineReviewSession session) {
        var value = session.factsJson().get("facts");
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> Map.copyOf((Map<String, Object>) item))
                    .toList();
        }
        return List.of();
    }

    private Map<String, Object> normalizedMap(ArchDoxNormalizedContext normalized) {
        var values = new LinkedHashMap<String, Object>();
        normalized.values().forEach((field, value) -> values.put(field, Map.of(
                "fieldName", value.fieldName(),
                "canonicalValue", value.canonicalValue(),
                "rawValue", value.rawValue(),
                "confidence", value.confidence())));
        return Map.of(
                "values", values,
                "missingFields", normalized.missingQuestions().stream()
                        .map(ArchDoxMissingContextQuestion::fieldName)
                        .toList(),
                "missingQuestions", normalized.missingQuestions().stream()
                        .map(question -> Map.of(
                                "fieldName", question.fieldName(),
                                "question", question.question(),
                                "required", question.required()))
                        .toList(),
                "ambiguities", normalized.ambiguities().stream()
                        .map(this::ambiguityMap)
                        .toList());
    }

    private Map<String, Object> ambiguityMap(ArchDoxContextAmbiguity ambiguity) {
        return Map.of(
                "fieldName", ambiguity.fieldName(),
                "rawValue", ambiguity.rawValue(),
                "candidates", ambiguity.candidates(),
                "question", ambiguity.question());
    }

    private EngineReviewSessionResponse toResponse(EngineReviewSession session) {
        return new EngineReviewSessionResponse(
                session.externalSessionId(),
                session.status().name(),
                session.customerProjectRef(),
                session.reviewPurpose(),
                session.documentTypeHint(),
                session.fileName(),
                facts(session),
                session.normalizedContextJson(),
                EngineValidationResult.responseFromJson(session.validationResultJson()),
                session.createdAt(),
                session.updatedAt(),
                session.normalizedAt(),
                session.completedAt());
    }

    private EngineReviewResultResponse toResultResponse(EngineReviewSession session) {
        var validationJson = session.validationResultJson();
        return new EngineReviewResultResponse(
                session.externalSessionId(),
                session.status().name(),
                validationJson != null && !validationJson.isEmpty(),
                EngineValidationResult.responseFromJson(validationJson),
                session.updatedAt(),
                session.normalizedAt(),
                session.completedAt());
    }

    private ArchDoxContextFactSource source(String value) {
        if (isBlank(value)) {
            return ArchDoxContextFactSource.CUSTOMER_AGENT_EXTRACTED;
        }
        try {
            return ArchDoxContextFactSource.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ArchDoxContextFactSource.CUSTOMER_AGENT_EXTRACTED;
        }
    }

    private double confidence(Object value) {
        if (value instanceof Number number) {
            return confidence(number.doubleValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return confidence(Double.parseDouble(text));
            } catch (NumberFormatException ignored) {
                return 0.5d;
            }
        }
        return 0.5d;
    }

    private double confidence(Double value) {
        if (value == null) {
            return 0.5d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
