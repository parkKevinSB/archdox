# ArchDox Current State

Last updated: 2026-05-29

This file is a short operational snapshot. It is not a replacement for the
architecture documents. Keep it current after major phase completions so AI
agents and humans can re-enter the project without reading every Markdown file.

## Platform Position

ArchDox is a document workflow orchestration platform for architecture office
workflows. Document generation is one important capability, but the system is
designed around the full lifecycle:

```text
project/site/target
-> report data and checklist input
-> photos and evidence
-> validation/review
-> template/output layout binding
-> ArchDox Agent rendering
-> artifact delivery and storage
-> operations and recovery
```

## Local Runtime

Current local development addresses:

| Component | Address |
| --- | --- |
| Client web | `http://127.0.0.1:5173` |
| Admin web | `http://127.0.0.1:5174` |
| Cloud API | `http://localhost:8080` |
| ArchDox Agent | `http://localhost:18080` |
| PostgreSQL | `localhost:55432` |
| MinIO API | `http://localhost:9000` |
| MinIO console | `http://localhost:9001` |
| MailHog SMTP/UI | `localhost:1025`, `http://localhost:8025` |

Local database convention:

```text
database: archdox
username: archdox
password: archdox
container: archdox-postgres-55432
```

Development accounts:

| Account | Password | Purpose |
| --- | --- | --- |
| `new-user@test.co.kr` | `password-1234` | normal user test account |
| `archdox-admin@test.co.kr` | `password-1234` | platform admin test account |

These are development-only credentials.

## Implemented Foundations

- Account, office, invitation, personal workspace, and role foundations.
- Project, site, inspection target, checklist schema, report, report revision,
  and permission foundations.
- Photo asset foundations, S3-compatible storage direction, working image and
  thumbnail policy, and local/office storage policy.
- ArchDox Agent registration, WebSocket control channel, command ACK/COMPLETE
  events, duplicate session policy, heartbeat/stale-session handling, and
  command routing foundation.
- Document job, artifact, delivery, status/progress, and recovery foundations.
- Agent-based document rendering with HTML, DOCX, and PDF export direction.
- Template/configuration registry foundations, template upload/storage, output
  layout, and neutral document data model direction.
- Preflight document review gate tied to report revision state.
- AI harness foundation for document review assistance, with code validation
  expected before AI validation.
- AI provider policy, office-level AI enablement, budget/call logging, and
  credential management foundations.
- Development fake AI provider can run real Flow/Harness paths without external
  API keys when explicitly enabled with a `fake-` provider code.
- `dev` and `local` profiles can bootstrap `fake-review` / `fake-ops`
  providers and attach fake review AI policy to existing offices, so manual UI
  testing can exercise the real AI orchestration path without a paid API key.
- Spring AI adapter is attached behind the ArchDox `AiModelGateway`; provider
  codes such as `spring-ai-*` can delegate to `flower-ai-harness-spring-ai`
  when explicitly enabled.
- Platform Admin AI management includes provider credentials, office policies,
  pricing rules, monthly usage summary, estimated cost, and recent call logs.
- Platform admin/Ops foundations separate from office admin roles.
- Platform ops workflow foundation records deterministic stuck detection as
  `platform_ops_runs`, `platform_ops_incidents`, and `platform_ops_findings`.
- Platform ops detection now runs through the `platform-ops` Flower worker;
  incident diagnosis also runs through the same worker, builds a redacted
  deterministic diagnosis snapshot, and can optionally submit `OpsDiagnosisHarness`
  to the `platform-ops-ai` worker when platform ops AI diagnosis is enabled.
- The platform admin UI can trigger incident diagnosis and show the latest
  diagnosis snapshot as operational summary, redaction policy, recent findings,
  and related operation events.
- Security baseline including login protection, rate limiting, security
  response conventions, and deployment edge policy direction.
- Optional document signature input before generation. Signatures are
  revision-specific job input, not user profile data, and templates decide
  whether to render or ignore them.
- Local E2E bootstrap script exists at `scripts/local-e2e/start-local-e2e.ps1`.
  It starts Docker dependencies, Cloud API, ArchDox Agent, client web, and admin
  web with fake AI enabled for AWS-deployment readiness testing.

## Current Architectural Policies

Cloud API must not render documents inline. REST creates durable state and
submits orchestration. The selected ArchDox Agent renders and uploads or stores
artifacts.

All ArchDox Agents share the same runtime concept. `LOCAL_OFFICE` and
`CLOUD_MANAGED` are deployment/configuration modes, not separate product
concepts.

Agent WebSocket is the control/event channel. Large artifacts and files should
move through HTTP/object storage/storage adapters, not as large WebSocket binary
payloads.

Only registered agents may connect. One logical `agent_id` may have one active
WebSocket session. Additional physical workers need separate agent registrations.

The durable source of truth is the database. Flower runtime state is recoverable
from durable job/session/command records until full Flower persistence is added.

The document source of truth is:

```text
report snapshot + template config + output layout
```

PDF, DOCX, HTML, and future HWP outputs are render artifacts, not the data
source of truth.

AI must be optional by office/user policy and must not replace deterministic
validation. Code-level validation handles required fields, checklist completion,
photo presence, permissions, and revision gates first. AI is for higher-level
review, wording assistance, risk hints, and finding generation.

## Test Focus

When manually testing the current system, focus on:

1. Normal user flow: login -> project -> site -> report -> checklist/photo ->
   preflight review -> optional signature -> document generation -> download.
2. Agent flow: registered Agent connects, receives document commands, renders,
   uploads/completes, and handles disconnect/reconnect policy.
3. Platform admin flow: platform admin views users, offices, agents, jobs,
   failures, and operational events separately from office admin screens.
4. Permission flow: personal user, office member, office admin, and platform
   admin must not be mixed.
5. Security flow: unauthenticated and excessive requests should fail before
   expensive DB or document work.
6. Local E2E bootstrap: start the stack with `scripts/local-e2e/start-local-e2e.ps1`
   and follow `docs/testing/LOCAL_E2E_TEST_BOOTSTRAP.md`.

## Known Follow-Up Areas

- Continue UI polish and Korean label mapping across client/admin screens.
- Validate real office templates against reference forms before production use.
- Prepare deployment scripts and secrets management for Lightsail/EC2.
- Add production edge controls such as Cloudflare/WAF, Nginx limits, TLS, and
  backup/restore playbooks.
- Continue operational monitoring around stuck jobs, agent health, photo pickup,
  delivery, and AI cost/budget alerts.
- Polish the platform admin AI diagnosis view around `AI_HARNESS` findings,
  suggested actions, confidence, and operator approval flow.
