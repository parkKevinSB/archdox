# ArchDox Engine Business Positioning

## Decision

ArchDox Engine Service should not be positioned as:

```text
AI reads building law and reviews your document.
```

That is too easy for a general AI agent to imitate.

The stronger positioning is:

```text
ArchDox converts architecture and construction-supervision documents into a
reviewable business context, applies effective-date-specific law, standards,
domain rules, and office workflow rules, then returns structured findings,
evidence, correction actions, missing-context questions, and an audit trail.
```

The product value is not only LLM response quality. The product value is a
repeatable compliance and workflow review engine.

## Product Thesis

General AI agents are useful for:

```text
summarizing documents
finding obvious omissions
improving wording
turning text into checklist form
searching general legal references
flagging risky language
```

ArchDox must be strong where general agents are weak:

```text
document type classification
construction/site context normalization
effective-date and law-version fixing
domain rule selection
consistent missing-item judgment
source/article/document-location mapping
durable review result storage
repeatability for the same input
customer-specific mapping profile support
audit-ready review evidence
```

If ArchDox does not own these capabilities, it becomes just another prompt or
wrapper around a general model.

## Differentiation Pillars

### 1. Context Normalization

External documents and customer data are rarely in ArchDox-native shape.

ArchDox must provide:

```text
Document / customer data
  -> Context Intake
  -> Context Normalization
  -> ArchDox Public Canonical Context
  -> Domain Context Translator
  -> Rule Engine / Review Engine
```

Example customer input:

```text
use: neighborhood living + office
structure: RC
work: foundation concrete placement
```

Canonical context:

```json
{
  "buildingUses": [
    "NEIGHBORHOOD_LIVING_FACILITY",
    "BUSINESS_FACILITY"
  ],
  "structureType": "REINFORCED_CONCRETE",
  "workType": "FOUNDATION_CONCRETE_PLACEMENT"
}
```

If context is ambiguous, ArchDox should ask a missing-context question instead
of inventing facts.

Examples:

```text
Which placement area is this: foundation, slab, wall, or another part?
What are the gross floor area and number of floors?
Is this document for pre-construction, construction supervision, or completion?
```

### 2. Effective-Date And Version Fixing

The review result must say which standard was used.

Minimum metadata:

```json
{
  "effectiveDate": "2026-06-04",
  "lawVersion": "source-backed law version",
  "ruleSetVersion": "inspection-rules-2026.06",
  "engineVersion": "archdox-engine-1.2.0"
}
```

This is a major B2B differentiator. A general model may mix current search
results, model memory, and unstated assumptions. ArchDox must fix the review
basis and store it.

### 3. Rule Engine

The core product is not the AI model. The core product is the rule repository
and rule evaluation layer.

Example:

```text
documentType = CONSTRUCTION_DAILY_SUPERVISION_LOG
workType = FOUNDATION_CONCRETE_PLACEMENT
structureType = REINFORCED_CONCRETE
phase = STRUCTURAL_FOUNDATION
  -> required checks:
     - reinforcement condition before placement
     - quality management during placement
     - completion photo
     - supervisor confirmation opinion
     - concrete delivery/test record where applicable
```

Rules may come from:

```text
official law and administrative rules
construction supervision standards
official forms and appendices
office workflow rules
domain expert verification
customer-specific templates and mapping profiles
```

AI may help explain or refine findings, but AI must not be the authority that
selects rules or invents legal obligations.

### 4. Structured Findings And Correction Actions

ArchDox findings should be structured, not loose prose.

Example:

```json
{
  "issueCode": "FOUNDATION_CONCRETE_PHOTO_MISSING",
  "severity": "WARNING",
  "requiredAction": "Attach before/during/after concrete placement photos.",
  "evidence": [
    {
      "documentLocation": "work summary section",
      "reason": "No placement completion photo was found."
    }
  ],
  "basis": [
    {
      "sourceType": "SUPERVISION_STANDARD",
      "sourceId": "CONSTRUCTION_SUPERVISION_DETAILED_STANDARD",
      "effectiveDate": "2026-06-04"
    }
  ]
}
```

This makes later analytics, user correction, billing, re-review, and audit
possible.

Correction suggestions should be concrete:

```text
Add these items to the supervision content section:
- reinforcement condition before foundation placement
- quality management during placement
- completion photo record
- supervisor confirmation opinion
```

Do not settle for vague suggestions such as "make the sentence clearer."

### 5. Audit Trail And Repeatability

Every external or internal engine review should be traceable.

Minimum review record:

```json
{
  "reviewId": "RVW-20260604-000123",
  "requestedBy": "customer-agent-01",
  "tenantId": "office-a",
  "inputHash": "sha256:...",
  "documentVersion": "v3",
  "effectiveDate": "2026-06-04",
  "ruleSetVersion": "2026.06",
  "engineVersion": "1.2.0",
  "issues": [],
  "missingContext": [],
  "createdAt": "2026-06-04T10:21:00+09:00"
}
```

This is what customers can trust in audits, disputes, and internal QA. The
valuable output is not just an answer. It is an answer with basis, version,
context, and reproducible trace.

### 6. Security And Data Handling

Construction supervision logs, site photos, project records, and office
documents can be sensitive.

The Engine product must support:

```text
tenant isolation
retention policy
post-review original deletion option
encryption
access logs
customer-specific storage policy
sensitive data masking
no model-training-use guarantee, where contractually required
```

Official privacy and AI risk-management guidance should be treated as product
requirements, not only legal footnotes. See:

```text
NIST AI Risk Management Framework
Korea PIPC generative AI personal information processing guide
```

## Legal And Compliance Positioning

ArchDox must not sell itself as an automatic legal-advice system.

Unsafe positioning:

```text
ArchDox provides building-law legal advice.
ArchDox confirms that this document has no legal problem.
```

Safer positioning:

```text
ArchDox assists architecture and construction-supervision document preparation.
ArchDox checks missing items against source-backed laws, standards, and domain rules.
ArchDox retrieves source references and produces review reports for expert confirmation.
The final judgment remains with the architect, supervisor, or qualified expert.
```

This does not weaken the product. It makes it more credible for B2B buyers.

## MVP Scope

Do not start with "review every building law issue."

The first commercial Engine MVP should be narrow:

```text
Construction daily supervision log review engine
```

Input:

```text
daily supervision document or ArchDox report snapshot
work type
site/project basic context
review effective date
available photos/evidence
```

Output:

```text
missing items
risk level
source-backed basis
correction text/action
missing-context questions
review report
review id and audit metadata
```

Expansion order:

```text
1. construction daily supervision log
2. work-type checklist review
3. photo/evidence omission review
4. quality-management document review
5. safety-management document review
6. start/completion-related document review
```

This is how ArchDox accumulates defensible rule assets.

## Channel Strategy

Build the engine first. Expose it through channels later.

```text
Document Intake
Context Normalizer
Rule Engine
Review Result
Correction Suggestion
Audit Log
```

Then expose the same engine through:

```text
ArchDox Web UI
ArchDox REST Engine API
ArchDox MCP Gateway
```

MCP is a valuable distribution channel, especially for Codex, Claude, Cursor,
ChatGPT, or customer-owned agents. But MCP is not the product core. The core is
the ArchDox Review Engine.

## Commercial Capability Checklist

ArchDox should not call the external product "ready" until these exist:

```text
1. document-to-review-context normalization
2. effective-date and source-version-fixed review
3. document/work-type/domain rule engine
4. structured findings with basis and correction actions
5. review history, audit log, customer mapping profile
6. authentication, quota, billing, and usage metering
7. privacy and retention controls
```

## References

- NIST AI Risk Management Framework:
  https://www.nist.gov/itl/ai-risk-management-framework
- Korea National Law Open Data:
  https://open.law.go.kr/LSO/openApi/guideList.do
- Korea Personal Information Protection Commission generative AI guide:
  https://www.pipc.go.kr/np/cop/bbs/selectBoardArticle.do?bbsId=BS217&mCode=D010030000&nttId=11439

## Short Summary

General AI says:

```text
This part may be a problem.
```

ArchDox should say:

```text
Using rule-set 2026.06 and the source-backed review basis for 2026-06-04,
three required records are missing from this foundation concrete placement
supervision log. Each finding includes the document location, basis, correction
action, and review id.
```

That is the difference customers can pay for.
