# ArchDox Agent Guide

This file is the entry point for AI-assisted development in this repository.
Before changing code, every agent must read this file and the referenced rule
documents.

## Required Reading

Read these documents before implementation:

1. `docs/development/AGENT_RULES.md`
2. `docs/development/DB_MIGRATION_RULES.md`
3. `docs/development/PHASE_EXECUTION_RULE.md`
4. `docs/development/DDD_EVENT_ORCHESTRATION_RULES.md`
5. `docs/architecture/DOMAIN_MODEL.md`
6. `docs/architecture/API_CONTRACT.md`
7. `docs/architecture/IMAGE_UPLOAD_POLICY.md`
8. `docs/architecture/ARCHDOX_AGENT_ARCHITECTURE.md`
9. `docs/architecture/CLOUD_API_SCALING_AND_ROUTING.md`

Use the existing detailed design documents as supporting references:

- `01_상세설계_전체아키텍처.md`
- `02_상세설계_도메인및데이터.md`
- `03_상세설계_API및이벤트.md`
- `08_구현순서_및_기능목록.md`
- `09_상세설계_문서생성및AI계층.md`
- `12_상세설계_문서전달정책.md`

## Repository Shape

- `cloud-api`: Spring Boot API server, auth, tenancy, DB-backed business APIs.
- `archdox-agent`: Spring Boot ArchDox Agent runtime. It can run as
  `LOCAL_OFFICE` for office/NAS execution or later as `CLOUD_MANAGED` for cloud
  document generation.
- `document-engine`: shared document generation engine used by cloud and agent.
- `domain-shared`: shared enums and domain primitives.
- `client/web`: React PWA client.
- `admin`: admin console.
- `infra`: local and deployment infrastructure files.

## Priority Rules

When documents conflict, apply this order:

1. User's latest explicit instruction.
2. `AGENTS.md`.
3. Files under `docs/development`.
4. Files under `docs/architecture`.
5. Existing detailed design documents.
6. Current implementation patterns.

If a conflict affects data ownership, security, tenant isolation, or API
compatibility, stop and ask for approval before changing behavior.

## Non-Negotiables

- Do not bypass office isolation.
- Do not add business tables without deciding `office_id` ownership.
- Do not change applied Flyway migrations; add a new versioned migration.
- Do not put document generation internals directly inside controller classes.
- Do not duplicate document generation logic between `cloud-api` and `archdox-agent`.
- Do not finish a phase without tests or a clear explanation of why a test could
  not run.
