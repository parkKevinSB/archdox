# ArchDox Platform Identity

ArchDox is not a simple document generator.

ArchDox is a document workflow and orchestration platform. Document files are
important outputs, but the platform's core value is coordinating the full
document lifecycle across field data, photos, templates, review, generation,
delivery, storage, agents, and operations.

## Core Identity

```text
ArchDox = Document Workflow Orchestration Platform
```

The platform should be designed around these ideas:

- A document is a workflow outcome, not just a file.
- Field data, inspection steps, photos, templates, legal checks, approvals, and
  delivery requests are all part of the document workflow.
- Cloud API owns tenant-safe state, REST contracts, progress records, and
  durable metadata.
- Flower owns long-running orchestration with steps, waits, retries, timeouts,
  and backoff.
- Bloom owns in-process event notification and module-internal flow signaling.
- ArchDox Agent owns office/cloud worker execution such as original pickup,
  document rendering, artifact upload, and storage access.
- DB tables are the source of truth for business state. Runtime flow state is
  recoverable from DB state until Flower persistence is added.

## Platform Capabilities

ArchDox should grow toward these workflow families:

- photo intake and original handoff
- working image and thumbnail generation
- inspection report data capture
- document generation
- document review
- template validation
- legal or rule-based checks
- approval and finalization
- document delivery and sharing
- operational monitoring and recovery
- configuration-based customer customization

## Naming Guidance

Prefer names that express workflow and lifecycle concepts:

- `document_jobs`: document workflow job state
- `document_artifacts`: generated outputs
- `document_delivery_requests`: delivery workflow requests
- `photo-pickup`: original photo handoff workflow
- `document-generation`: document generation workflow
- `document-delivery`: artifact delivery workflow
- `ArchDox Agent`: runtime that can execute workflow commands in local office or
  cloud-managed mode

Avoid narrowing architecture language to "DOCX generator" or "local document
server." Those are implementation details inside a larger workflow platform.

## Architectural Consequence

When adding a feature, ask:

1. What durable business state records this workflow?
2. What events express the important facts?
3. Which Flower flow owns waiting, retry, timeout, and progression?
4. Which actor executes the work: Cloud, ArchDox Agent, user, admin, or future
   Ops Agent?
5. How does the UI observe progress through REST polling or a future push
   channel?
6. How does the workflow recover after Cloud API or Agent restart?
7. Is this behavior a stable engine capability or a customer-specific variant
   that belongs in a versioned template/configuration?

If a feature has lifecycle, waiting, retry, or cross-runtime coordination, treat
it as a workflow first and a service method second.

If a feature differs by office, report type, output layout, approval stage, or
required checklist, treat it as a configuration candidate before adding Java
branches. See `CONFIGURATION_BASED_CUSTOMIZATION.md`.
