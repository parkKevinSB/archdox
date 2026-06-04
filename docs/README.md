# ArchDox Documentation Map

This is the entry point for ArchDox documentation.

ArchDox has many focused Markdown files because the platform spans REST APIs,
workflow orchestration, agents, document rendering, UI, operations, security,
and AI-assisted review. Do not read every document at the start of a task.
Read the small canonical set first, then open only the topic-specific files
needed for the change.

## Read First

1. [architecture/ARCHDOX_PLATFORM_IDENTITY.md](architecture/ARCHDOX_PLATFORM_IDENTITY.md)
   - Defines ArchDox as a document workflow orchestration platform, not a
     narrow document generator.
2. [development/AGENT_RULES.md](development/AGENT_RULES.md)
   - Non-negotiable development rules for AI agents and human contributors.
3. [CURRENT_STATE.md](CURRENT_STATE.md)
   - Current implementation state, local runtime addresses, test accounts, and
     active architectural policies.
4. [architecture/DOMAIN_MODEL.md](architecture/DOMAIN_MODEL.md)
   - Core domain concepts: office, user, project, site, target, report, photo,
     template, document job, agent, and artifact.

## Topic Guide

| Work area | Read these files |
| --- | --- |
| REST API contracts | [architecture/API_CONTRACT.md](architecture/API_CONTRACT.md) |
| Database migrations | [development/DB_MIGRATION_RULES.md](development/DB_MIGRATION_RULES.md) |
| DDD events, Bloom, Flower | [development/DDD_EVENT_ORCHESTRATION_RULES.md](development/DDD_EVENT_ORCHESTRATION_RULES.md), [architecture/FLOW_RECOVERY_POLICY.md](architecture/FLOW_RECOVERY_POLICY.md) |
| Agent architecture | [architecture/ARCHDOX_AGENT_ARCHITECTURE.md](architecture/ARCHDOX_AGENT_ARCHITECTURE.md), [architecture/CLOUD_API_SCALING_AND_ROUTING.md](architecture/CLOUD_API_SCALING_AND_ROUTING.md) |
| Document engine and formats | [architecture/DOCUMENT_NEUTRAL_MODEL.md](architecture/DOCUMENT_NEUTRAL_MODEL.md), [architecture/KOREAN_DOCUMENT_FORMAT_STRATEGY.md](architecture/KOREAN_DOCUMENT_FORMAT_STRATEGY.md), [DOCUMENT_RENDER_RUNTIME_POLICY.md](DOCUMENT_RENDER_RUNTIME_POLICY.md) |
| Images and storage | [architecture/IMAGE_UPLOAD_POLICY.md](architecture/IMAGE_UPLOAD_POLICY.md), [architecture/DEPLOYMENT_PORTABILITY.md](architecture/DEPLOYMENT_PORTABILITY.md) |
| Configuration and templates | [architecture/CONFIGURATION_BASED_CUSTOMIZATION.md](architecture/CONFIGURATION_BASED_CUSTOMIZATION.md) |
| Domain assets and construction supervision catalog | [architecture/CONSTRUCTION_SUPERVISION_DOMAIN_CATALOG.md](architecture/CONSTRUCTION_SUPERVISION_DOMAIN_CATALOG.md), [architecture/SITE_SUPERVISION_LEDGER_ARCHITECTURE.md](architecture/SITE_SUPERVISION_LEDGER_ARCHITECTURE.md) |
| Project/site/target hierarchy | [architecture/SITE_TARGET_HIERARCHY.md](architecture/SITE_TARGET_HIERARCHY.md), [architecture/SITE_SUPERVISION_LEDGER_ARCHITECTURE.md](architecture/SITE_SUPERVISION_LEDGER_ARCHITECTURE.md) |
| User-facing UI | [architecture/CLIENT_PRODUCT_UI_DIRECTION.md](architecture/CLIENT_PRODUCT_UI_DIRECTION.md), [architecture/UI_WORKFLOW_ORCHESTRATION.md](architecture/UI_WORKFLOW_ORCHESTRATION.md) |
| React structure | [architecture/FRONTEND_ARCHITECTURE.md](architecture/FRONTEND_ARCHITECTURE.md), [architecture/FRONTEND_STACK_DECISION.md](architecture/FRONTEND_STACK_DECISION.md) |
| Security | [architecture/SECURITY_POLICY.md](architecture/SECURITY_POLICY.md) |
| Operations and platform admin | [architecture/OPERATIONS_AND_ADMIN.md](architecture/OPERATIONS_AND_ADMIN.md) |
| AI harness and AI review | [ai-harness/README.md](ai-harness/README.md), [architecture/AI_PROVIDER_POLICY.md](architecture/AI_PROVIDER_POLICY.md) |
| Office knowledge platform direction | [architecture/OFFICE_KNOWLEDGE_PLATFORM_STRATEGY.md](architecture/OFFICE_KNOWLEDGE_PLATFORM_STRATEGY.md) |
| Engine boundary, external Engine API, and business positioning | [architecture/ARCHDOX_ENGINE_BOUNDARY.md](architecture/ARCHDOX_ENGINE_BOUNDARY.md), [architecture/ARCHDOX_ENGINE_SERVICE_STRATEGY.md](architecture/ARCHDOX_ENGINE_SERVICE_STRATEGY.md), [architecture/ARCHDOX_ENGINE_BUSINESS_POSITIONING.md](architecture/ARCHDOX_ENGINE_BUSINESS_POSITIONING.md), [architecture/ARCHDOX_MCP_GATEWAY_STRATEGY.md](architecture/ARCHDOX_MCP_GATEWAY_STRATEGY.md) |
| Public site and domain routing | [architecture/PUBLIC_SITE_AND_DOMAIN_STRATEGY.md](architecture/PUBLIC_SITE_AND_DOMAIN_STRATEGY.md) |
| Legal domain and compliance review | [architecture/LEGAL_DOMAIN_ARCHITECTURE.md](architecture/LEGAL_DOMAIN_ARCHITECTURE.md) |
| Git workflow | [development/GIT_WORKFLOW.md](development/GIT_WORKFLOW.md) |
| Phase discipline | [development/PHASE_EXECUTION_RULE.md](development/PHASE_EXECUTION_RULE.md) |
| Korean reference forms | [reference-forms/korean/README.md](reference-forms/korean/README.md) |

## Document Status

- Canonical architecture documents live under `docs/architecture`.
- Non-negotiable implementation rules live under `docs/development`.
- AI harness design is intentionally isolated under `docs/ai-harness`.
- Reference source materials live under `docs/reference-forms`.
- `docs/CURRENT_STATE.md` is the short operational snapshot and should be
  updated after major phase completions.

## Reading Rule For AI Agents

Use this order:

1. Read this file.
2. Read `CURRENT_STATE.md`.
3. Read `development/AGENT_RULES.md`.
4. Read only the topic-specific files for the requested task.

If documents appear to conflict, prefer the stricter platform rule in this
order:

1. `development/AGENT_RULES.md`
2. `architecture/ARCHDOX_PLATFORM_IDENTITY.md`
3. Topic-specific architecture documents
4. Older phase notes or conversation history

When a major implementation changes the actual system behavior, update the
smallest relevant architecture document and `CURRENT_STATE.md`. Do not create a
new Markdown file when an existing canonical document is the right home.
