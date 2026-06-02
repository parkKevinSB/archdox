# archdox-agent

`archdox-agent` is the ArchDox execution runtime for document, photo,
artifact, and storage work.

It is not an AI agent.

The word `Agent` here means a registered runtime process connected to
`cloud-api`. It receives small WebSocket control commands, fetches larger
payloads over HTTP or storage adapters, runs `document-engine`, stores or
uploads artifacts, and reports command status back to Cloud API.

## Responsibilities

- Connect to Cloud API through the ArchDox Agent WebSocket control channel.
- Authenticate as a registered `archdox_agents` record.
- Execute commands such as `PHOTO_PICKUP`, `GENERATE_DOCUMENT`, and
  `UPLOAD_DOCUMENT_ARTIFACT`.
- Run `document-engine` for DOCX/HTML/PDF generation.
- Use configured storage profiles: `LOCAL_FILE`, `NAS`, or `S3_COMPATIBLE`.
- Report ACK, COMPLETE, and FAIL events with machine-readable error codes.

## Not Responsibilities

- AI prompt planning or model calls.
- Report preflight AI review.
- Worker chat conversation planning.
- User permission policy.
- REST API contracts or UI state.
- Office/project/report source-of-truth ownership.

Those belong to `cloud-api`, `archdox-worker`, and `archdox-ai-harness`.

## Deployment Modes

The same runtime can be deployed in different modes:

| Mode | Meaning |
| --- | --- |
| `LOCAL_OFFICE` | Runs near an office PC, NAS, or local disk. Used when an office wants originals/artifacts stored locally. |
| `CLOUD_MANAGED` | Runs in ArchDox-managed cloud infrastructure. Used for personal accounts or offices without a local runtime. |

The deployment mode changes configuration and storage, not the identity of the
runtime.

## Relationship To Other Modules

```text
cloud-api
  -> owns jobs, permissions, orchestration, command records
  -> sends control commands to archdox-agent

archdox-agent
  -> executes document/photo/artifact/storage commands
  -> reports result events

document-engine
  -> pure document generation primitives used by archdox-agent

archdox-ai-harness
  -> AI review/planning blocks, unrelated to document binary execution

archdox-worker
  -> controlled domain action layer that may request document generation,
     but does not render files directly
```

## Naming Rule

Use `ArchDox Agent` only for this registered document/file execution runtime.
Do not use it to mean an AI worker, chat planner, or autonomous LLM agent.

For architecture details, see
`docs/architecture/ARCHDOX_AGENT_ARCHITECTURE.md`.
