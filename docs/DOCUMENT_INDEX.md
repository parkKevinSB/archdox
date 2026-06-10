# ArchDox Document Index

Level: L0
Status: canonical
Last reviewed: 2026-06-10

This index assigns level and status to the current Markdown document set. It is
the current source of truth for document priority until front matter is added to
all canonical files.

## L0 Entry And Governance

| Document | Level | Status | Role |
| --- | --- | --- | --- |
| `docs/README.md` | L0 | canonical | Documentation entry point and topic guide. |
| `docs/DOCUMENT_GOVERNANCE.md` | L0 | canonical | Document hierarchy, conflict rules, update rules. |
| `docs/DOCUMENT_INDEX.md` | L0 | canonical | Current document level/status registry. |
| `docs/architecture/SYSTEM_MAP.md` | L0 | canonical | Current system map and module responsibility entry point. |
| `docs/development/AGENT_RULES.md` | L0 | canonical | Non-negotiable AI-agent and implementation rules. |
| `docs/CURRENT_STATE.md` | L0 | active-snapshot | Short operational state and re-entry snapshot. |

## L1 Canonical Architecture Contracts

| Document | Level | Status | Role |
| --- | --- | --- | --- |
| `docs/architecture/ARCHDOX_PLATFORM_IDENTITY.md` | L1 | canonical | Product identity and platform scope. |
| `docs/architecture/DOMAIN_MODEL.md` | L1 | canonical | Core domain concepts and ownership. |
| `docs/architecture/ARCHDOX_ENGINE_BOUNDARY.md` | L1 | canonical | Engine boundary, SaaS/internal/external split. |
| `docs/architecture/ARCHDOX_MCP_GATEWAY_STRATEGY.md` | L1 | canonical | MCP gateway strategy and future external adapter direction. |
| `docs/architecture/ARCHDOX_AGENT_ARCHITECTURE.md` | L1 | canonical | Agent identity, deployment modes, responsibilities. |
| `docs/architecture/LEGAL_DOMAIN_ARCHITECTURE.md` | L1 | canonical | Legal corpus, legal sync, digest, review boundaries. |
| `docs/architecture/AI_PROVIDER_POLICY.md` | L1 | canonical | AI provider, harness policy, budget and operations direction. |
| `docs/architecture/SECURITY_POLICY.md` | L1 | canonical | Security, authentication, exposure, and operational controls. |
| `docs/architecture/OPERATIONS_AND_ADMIN.md` | L1 | canonical | Platform admin and operations model. |
| `docs/architecture/DOCUMENT_NEUTRAL_MODEL.md` | L1 | canonical | Structured document model and output separation. |
| `docs/architecture/CONFIGURATION_BASED_CUSTOMIZATION.md` | L1 | canonical | Configuration/template customization policy. |
| `docs/architecture/CONSTRUCTION_SUPERVISION_DOMAIN_CATALOG.md` | L1 | canonical | Construction-supervision domain catalog contract. |
| `docs/architecture/SITE_SUPERVISION_LEDGER_ARCHITECTURE.md` | L1 | canonical | Site ledger and reusable supervision data architecture. |
| `docs/architecture/IMAGE_UPLOAD_POLICY.md` | L1 | canonical | Photo asset, working image, storage, and upload policy. |
| `docs/architecture/FLOW_RECOVERY_POLICY.md` | L1 | canonical | Flow recovery and runtime interruption rules. |
| `docs/architecture/PUBLIC_SITE_AND_DOMAIN_STRATEGY.md` | L1 | canonical | Public/app/admin/api/mcp host strategy. |
| `docs/ai-harness/ARCHITECTURE.md` | L1 | canonical | AI harness architecture boundary. |
| `docs/ai-harness/README.md` | L1 | canonical | AI harness document map. |
| `docs/development/DDD_EVENT_ORCHESTRATION_RULES.md` | L1 | canonical | DDD events, Bloom, Flower orchestration rules. |
| `docs/development/DB_MIGRATION_RULES.md` | L1 | canonical | Database migration discipline. |
| `docs/development/PHASE_EXECUTION_RULE.md` | L1 | canonical | Phase execution and implementation sequencing rules. |

## L2 Implementation Details, Guides, And Procedures

| Document | Level | Status | Role |
| --- | --- | --- | --- |
| `docs/architecture/API_CONTRACT.md` | L2 | canonical | REST/API request-response contract. |
| `docs/architecture/ARCHDOX_ENGINE_SERVICE_STRATEGY.md` | L2 | canonical | Engine service extraction and scaling strategy. |
| `docs/architecture/ARCHDOX_ENGINE_BUSINESS_POSITIONING.md` | L2 | canonical | Engine product/business positioning. |
| `docs/architecture/CLOUD_API_SCALING_AND_ROUTING.md` | L2 | canonical | Cloud API scaling and routing details. |
| `docs/architecture/DEPLOYMENT_PORTABILITY.md` | L2 | canonical | Runtime portability, storage, and Docker Compose operation. |
| `docs/architecture/FRONTEND_ARCHITECTURE.md` | L2 | canonical | Frontend code structure. |
| `docs/architecture/FRONTEND_STACK_DECISION.md` | L2 | canonical | Frontend stack decision record. |
| `docs/architecture/CLIENT_PRODUCT_UI_DIRECTION.md` | L2 | canonical | User-facing product UI direction. |
| `docs/architecture/UI_WORKFLOW_ORCHESTRATION.md` | L2 | canonical | UI workflow orchestration rules. |
| `docs/architecture/SITE_TARGET_HIERARCHY.md` | L2 | canonical | Project/site/target hierarchy details. |
| `docs/architecture/KOREAN_DOCUMENT_FORMAT_STRATEGY.md` | L2 | canonical | Korean document rendering and format strategy. |
| `docs/architecture/OFFICE_KNOWLEDGE_PLATFORM_STRATEGY.md` | L2 | canonical | Office knowledge platform direction. |
| `docs/DOCUMENT_RENDER_RUNTIME_POLICY.md` | L2 | canonical | Document render runtime policy. |
| `docs/ai-harness/DATA_AND_SCHEMA.md` | L2 | canonical | AI harness data and schema details. |
| `docs/ai-harness/FLOWER_BLOOM_RUNTIME.md` | L2 | canonical | Flower/Bloom runtime integration details. |
| `docs/ai-harness/HARNESS_TYPES.md` | L2 | canonical | Harness type definitions. |
| `docs/ai-harness/ARCHDOX_AI_ORCHESTRATION_FLOW.md` | L2 | canonical | AI orchestration flow details. |
| `docs/ai-harness-worker-evaluation.md` | L2 | guide | AI/worker evaluation scenario and scoring guide. |
| `docs/worker-service/README.md` | L2 | canonical | Worker service architecture and future extraction guide. |
| `docs/development/GIT_WORKFLOW.md` | L2 | guide | Git workflow and commit discipline. |
| `docs/testing/LOCAL_E2E_TEST_BOOTSTRAP.md` | L2 | guide | Local E2E startup and smoke procedure. |
| `docs/reference-forms/korean/README.md` | Reference | reference | Korean form source/reference material. |

## L3 Roadmaps And Planning Material

| Document | Level | Status | Role |
| --- | --- | --- | --- |
| `docs/ai-harness/IMPLEMENTATION_ROADMAP.md` | L3 | roadmap | Planned AI harness implementation direction. Verify against code before using. |
| `docs/old/initial-design/README.md` | L3 | superseded | Index for archived initial Korean design notes. Historical record only; do not use for implementation. |

## Current Superseded Documents

| Document | Former Location | Role |
| --- | --- | --- |
| `docs/old/initial-design/00_README.md` | `00_README.md` | Initial design archive index. Historical record only. |
| `docs/old/initial-design/01_상세설계_전체아키텍처.md` | `01_상세설계_전체아키텍처.md` | Initial overall architecture design. Historical record only. |
| `docs/old/initial-design/02_상세설계_도메인및데이터.md` | `02_상세설계_도메인및데이터.md` | Initial domain/data design. Historical record only. |
| `docs/old/initial-design/03_상세설계_API및이벤트.md` | `03_상세설계_API및이벤트.md` | Initial API/event design. Historical record only. |
| `docs/old/initial-design/04_상세설계_이미지및스토리지.md` | `04_상세설계_이미지및스토리지.md` | Initial image/storage design. Historical record only. |
| `docs/old/initial-design/05_상세설계_클라이언트.md` | `05_상세설계_클라이언트.md` | Initial client design. Historical record only. |
| `docs/old/initial-design/06_상세설계_로컬서버_및_관리UI.md` | `06_상세설계_로컬서버_및_관리UI.md` | Initial local server/admin UI design. Historical record only. |
| `docs/old/initial-design/07_상세설계_멀티테넌트_admin.md` | `07_상세설계_멀티테넌트_admin.md` | Initial multi-tenant admin design. Historical record only. |
| `docs/old/initial-design/08_구현순서_및_기능목록.md` | `08_구현순서_및_기능목록.md` | Initial implementation sequence and feature list. Historical record only. |
| `docs/old/initial-design/09_상세설계_문서생성및AI계층.md` | `09_상세설계_문서생성및AI계층.md` | Initial document generation and AI layer design. Historical record only. |
| `docs/old/initial-design/10_상세설계_템플릿편집기.md` | `10_상세설계_템플릿편집기.md` | Initial template editor design. Historical record only. |
| `docs/old/initial-design/11_상세설계_건축법검토.md` | `11_상세설계_건축법검토.md` | Initial building-law review design. Historical record only. |
| `docs/old/initial-design/12_상세설계_문서전달정책.md` | `12_상세설계_문서전달정책.md` | Initial document delivery policy. Historical record only. |
| `docs/old/initial-design/건축자동화_개발구현계획.md` | `건축자동화_개발구현계획.md` | Initial development plan. Historical record only. |

Do not use superseded documents for new implementation unless a human explicitly
asks for historical context.
