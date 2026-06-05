# archdox-ai-harness

`archdox-ai-harness` contains ArchDox-specific AI harness definitions built on top of `flower-ai-harness`.

This module owns ArchDox prompt builders, input/output schemas, validation wiring, and finding extractors for bounded AI tasks such as document QA and report preflight review. It does not own REST controllers, authentication, office policy lookup, database writes, or UI state transitions. Those remain in `cloud-api`, which submits and orchestrates harness work through ArchDox Flower flows.

## Relationship To `flower-ai-harness`

`flower-ai-harness` is the generic AI run lifecycle framework. It knows how to
execute one AI task safely, but it does not know what an ArchDox report,
office, project, photo, checklist, or document job is.

`archdox-ai-harness` is the ArchDox adapter layer on top of that framework. It
turns ArchDox domain snapshots into prompts, validates ArchDox-specific
responses, and maps AI findings back into ArchDox concepts.

```text
flower-ai-harness
  = generic AI execution skeleton

archdox-ai-harness
  = ArchDox-specific AI work definitions using that skeleton
```

This module is also separate from `archdox-agent`. `archdox-agent` renders
documents and moves files; it does not run AI review/planning logic.

Current responsibilities:

- Document QA harness specs and result mapping
- Report preflight review harness specs and result mapping
- Worker conversation planner harness specs and result mapping
- Legal change digest harness specs and source-backed result mapping
- ArchDox-specific prompt/schema/finding contracts
- Testable fake-provider harness behavior without external API keys

## Report Preflight Context Rules

Report preflight review must evaluate a structured ArchDox snapshot, not the
final rendered document and not raw database rows. `cloud-api` prepares the
snapshot, and this module turns it into a model prompt.

The preflight input includes:

- report metadata and revision state
- saved step payloads
- deterministic validation findings
- uploaded photo evidence summary
- report-type compliance review guide

Photo evidence is represented by metadata such as `photoId`, `stepCode`,
`status`, uploaded asset flags, dimensions, and original pickup policy. Raw
image bytes and long-term original files are not sent to the model by default.
The top-level `photos` array is the source of truth for uploaded photo
evidence; `PHOTOS` step payload may be empty even when photos were uploaded
through the photo API. `originalPickupStatus=NOT_REQUIRED` is normal ArchDox
storage policy and must not be treated as missing evidence when a working image
is uploaded.

Not responsibilities:

- Generic AI provider abstraction
- Generic Flower runtime behavior
- Platform authorization or billing policy
- Long-running business workflow orchestration
- Direct document rendering or artifact storage

Future direction:

Some pieces may become generally useful outside ArchDox, especially harness observation, trace export, cost/usage snapshots, or reusable provider adapters. Those should first prove themselves here. If they become product-neutral, they can later be extracted into separate `flower-ai-harness-*` modules. Until then, this module stays intentionally ArchDox-specific.
