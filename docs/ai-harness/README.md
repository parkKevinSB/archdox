# ArchDox AI Harness

This folder describes how ArchDox uses AI harnesses inside the document
workflow platform.

AI is not the source of truth. ArchDox source data remains:

```text
report snapshot
+ template config
+ output layout
+ photos / evidence metadata
-> document-engine
-> HTML / DOCX / PDF
```

AI harnesses assist around that source data:

```text
before document generation
  -> find missing fields, weak evidence, inconsistent dates, legal/business
     risk signals, and checklist gaps

after document generation
  -> inspect generated output quality, formatting risk, missing sections,
     unresolved placeholders, and photo/table consistency
```

## Module Boundary

| Module | Responsibility |
| --- | --- |
| `flower-ai-harness` | Generic AI execution lifecycle: prompt, model call, validation, retry/refine, findings, fake-provider tests. |
| `archdox-ai-harness` | ArchDox-specific AI harnesses built with `flower-ai-harness`: report preflight, document QA, conversation planning, operations diagnosis. |
| `archdox-worker` | Controlled action layer that decides which allowed ArchDox action may run and executes it through Flower. |
| `archdox-agent` | Registered document/photo/artifact execution runtime. It is not an AI agent. |

## What AI May Do

- Find likely problems.
- Explain evidence.
- Suggest corrections.
- Draft text when the user or workflow allows it.
- Help review legal/business/checklist consistency.
- Produce structured findings for the UI.

## What AI Must Not Do

- Invent site facts that the user did not provide.
- Pretend a missing photo or attachment exists.
- Mutate report/document state directly without a registered action.
- Bypass deterministic validation, policy gates, or office permissions.
- Replace the user's final review and approval.

## Documents

- [ARCHITECTURE.md](ARCHITECTURE.md): module/process/layer boundaries.
- [ARCHDOX_AI_ORCHESTRATION_FLOW.md](ARCHDOX_AI_ORCHESTRATION_FLOW.md):
  business-level AI orchestration flow boundary and phases.
- [FLOWER_BLOOM_RUNTIME.md](FLOWER_BLOOM_RUNTIME.md): Flower/Bloom runtime
  direction.
- [HARNESS_TYPES.md](HARNESS_TYPES.md): document QA, legal review, template
  candidate, and operations harness types.
- [DATA_AND_SCHEMA.md](DATA_AND_SCHEMA.md): run, step, finding, and evidence
  data shape.
- [IMPLEMENTATION_ROADMAP.md](IMPLEMENTATION_ROADMAP.md): implementation
  phases.

## MVP Decision

The first version runs inside the Cloud API process:

```text
Code structure:
  flower-ai-harness library
  + archdox-ai-harness Gradle module

Runtime structure:
  cloud-api process

Future option:
  separate ai-harness-worker process if load, cost, or isolation demands it
```

The first ArchDox-owned harnesses are:

- Report Preflight Harness
- Document QA Harness
- Worker Conversation Planner Harness
- Ops Diagnosis Harness

If observer, trace export, provider health, or fake provider fixtures become
useful outside ArchDox, they may later be extracted into generic
`flower-ai-harness-*` modules. Until then, they should prove themselves inside
ArchDox first.

## Observation Mode

AI evaluation needs more than a success flag. During testing, Platform Admin can
temporarily enable an in-memory observation mode:

```text
AI request submitted
  -> capture rendered prompt messages
  -> capture model/provider/resource metadata
AI response received
  -> capture raw model text
  -> capture token usage, latency, finish reason, provider trace
Harness validation/finding pipeline
  -> existing trace events and findings remain the durable operational record
```

This mode is intentionally not a DB-backed customer-data log. It keeps only the
recent bounded buffer, expires entries by TTL, clears on disable, and disappears
on process restart. The purpose is model evaluation and prompt debugging:

- Was the prompt giving the model enough context?
- Did the model return valid structured JSON?
- Were the final findings faithful to the raw response?
- Is the chosen model too weak, too verbose, too strict, or too lenient?

Permanent records remain `ai_model_call_logs`, harness trace events, findings,
operation events, and document/report state. Raw prompt and raw response access
must stay platform-admin scoped.
