# ArchDox Agent Architecture

This document defines the official Agent concept. Do not use `Local Agent` as a
domain name. Locality is a deployment property, not the server's identity.

## Official Name

- Module name: `archdox-agent`
- Runtime name: `ArchDox Agent`
- Cloud DB prefix: `archdox_agent_*`
- Java package in the executable module: `com.archdox.agent`

## Core Concept

`ArchDox Agent` is the execution runtime for document/photo/artifact work.

It can run in more than one deployment mode:

- `LOCAL_OFFICE`: installed for an office, usually near NAS/local disks.
- `CLOUD_MANAGED`: operated by ArchDox in cloud, usually backed by
  S3-compatible storage.

The document generation layer must stay the same across both modes:

```text
Cloud API
  -> create job / validate office / submit Flower flow / update progress
  -> send GENERATE_DOCUMENT command
  -> for later download, send UPLOAD_DOCUMENT_ARTIFACT command when needed

ArchDox Agent
  -> receive command
  -> run document-engine
  -> store artifacts through configured storage profile
  -> upload prepared artifact copies for Cloud download delivery
  -> report ACK / COMPLETE / FAIL
```

## Agent Layers

```text
archdox-agent
  cloud
    WebSocket client, command DTOs, ACK/COMPLETE/FAIL reporting
  command
    Command executors such as PHOTO_PICKUP, GENERATE_DOCUMENT,
    UPLOAD_DOCUMENT_ARTIFACT
  document
    Agent-side document artifact storage and document-engine integration
  photo
    Agent-side original photo pickup storage
  storage
    Storage profile boundary: LOCAL_FILE, NAS, S3_COMPATIBLE
  config
    deploymentMode, Cloud API endpoint, credentials, storage profile
```

Current code keeps command executors under `cloud` because the MVP is small.
When the package grows, command executors should move to a dedicated
`command` package without changing the protocol.

## Registration And Authentication

ArchDox Agent must be registered before it can connect, even when the Agent runs
on the same Linux host as Cloud API.

The normal lifecycle is:

1. Office admin or platform admin issues an install token for a specific
   `agentCode` and `deploymentMode`.
2. Cloud creates or reuses the `archdox_agents` row and binds the install token
   to that Agent.
3. The Agent sends `HELLO` with `authMode=INSTALL_TOKEN`, the same `agentCode`,
   office id, deployment mode, and the one-time token.
4. Cloud returns `agentId` and `deviceSecret` once.
5. The operator stores `AGENT_ID` and `AGENT_DEVICE_SECRET`, removes
   `AGENT_INSTALL_TOKEN`, and restarts.
6. Future WebSocket connections use `authMode=DEVICE_SECRET`.

The registered deployment mode is authoritative. A paired Agent cannot switch
from `LOCAL_OFFICE` to `CLOUD_MANAGED`, or the reverse, just by changing local
config. Register a separate Agent code for that.

Naming recommendation:

- office PC/NAS main runtime: `office-main`
- additional office runtime: `office-backup-1`
- managed cloud runtime: `cloud-managed-1`, `cloud-managed-2`

`AGENT_SHARED_SECRET` is disabled by default. It is only a temporary development
fallback when Cloud API is explicitly configured with
`AGENT_ALLOW_SHARED_SECRET_AUTH=true`.

## Storage Profile

Agent storage must be configuration-driven. Cloud must not infer storage from
the agent name.

Official storage kinds:

- `LOCAL_FILE`: normal local disk or mounted drive path on the Agent machine.
- `NAS`: office network storage or mapped network share.
- `S3_COMPATIBLE`: AWS S3, MinIO, Wasabi, R2, Naver Object Storage, or similar.

`LOCAL_FS` is accepted only as a compatibility alias for older local
configuration. New configuration and documentation should use `LOCAL_FILE`.

The Agent may know absolute paths. Cloud must not. The `storageProfile` sent to
Cloud reports only safe capability metadata such as `kind`,
`fileSystemBacked`, `rootConfigured`, `bucket`, and `prefix`. It must not
include `rootPath` or any office PC/NAS absolute path.

```yaml
archdox:
  agent:
    deployment-mode: LOCAL_OFFICE
    storage:
      original:
        kind: LOCAL_FILE
        root-path: D:/ArchDox/original
      working:
        kind: LOCAL_FILE
        root-path: D:/ArchDox/working
      artifact:
        kind: LOCAL_FILE
        root-path: D:/ArchDox/artifacts
      template:
        kind: LOCAL_FILE
        root-path: D:/ArchDox/templates
```

For an office PC without a NAS, this is still valid:

```yaml
archdox:
  agent:
    deployment-mode: LOCAL_OFFICE
    storage:
      original:
        kind: LOCAL_FILE
        root-path: D:/ArchDoxStorage/originals
      artifact:
        kind: LOCAL_FILE
        root-path: D:/ArchDoxStorage/artifacts
```

For office NAS or a mapped network share:

```yaml
archdox:
  agent:
    deployment-mode: LOCAL_OFFICE
    storage:
      original:
        kind: NAS
        root-path: //office-nas/ArchDox/originals
      artifact:
        kind: NAS
        root-path: Z:/ArchDox/artifacts
```

For cloud-managed execution:

```yaml
archdox:
  agent:
    deployment-mode: CLOUD_MANAGED
    storage:
      original:
        kind: S3_COMPATIBLE
        bucket: archdox-original-temp
      working:
        kind: S3_COMPATIBLE
        bucket: archdox-working
      artifact:
        kind: S3_COMPATIBLE
        bucket: archdox-artifacts
```

Implemented Phase 9-1/9-2 behavior:

- `LOCAL_FILE` and `NAS` are filesystem-backed and use the same safe logical-ref
  resolver inside the Agent.
- `S3_COMPATIBLE` is implemented through an Agent-side S3-compatible adapter
  using the same logical storage refs.
- S3-compatible connection settings are runtime configuration:
  `AGENT_S3_ENDPOINT`, `AGENT_S3_REGION`, `AGENT_S3_ACCESS_KEY`,
  `AGENT_S3_SECRET_KEY`, and `AGENT_S3_PATH_STYLE_ACCESS`.
- Logical refs such as `documents/jobs/7001/report.docx` are mapped under the
  configured Agent root. Cloud stores logical refs, not physical roots.

## Connection Profile

The Agent connects outbound to Cloud API. The connection URL is configuration,
not code.

```text
AWS hosted:
wss://api.example.com/agent/ws

Office/home server:
ws://192.168.0.10:8080/agent/ws

Tailscale/private network:
ws://archdox-server.tailnet-name.ts.net:8080/agent/ws
```

The same ArchDox Agent runtime must work in all three cases. The differences are
deployment profile, endpoint URL, credentials, and storage profile.

If Cloud API runs behind a load balancer, the Agent may connect to any API
instance. Command truth stays in `archdox_agent_commands`, connection visibility
stays in `archdox_agent_sessions`, and the instance with the live WebSocket acts
only as transport.

## Connection Health Monitoring

Agent connection health is controlled by Cloud API Flower orchestration.

- ArchDox Agent sends lightweight `HEARTBEAT` messages over WebSocket.
- `AgentWebSocketHandler` only decodes transport messages and updates session
  visibility through application services.
- `archdox_agent_sessions.last_seen_at` is the durable connection signal.
- The `monitoring` Flower worker submits a long-lived
  `agent-connection-health-monitor` flow on application startup.
- The monitor flow loops through `CHECK -> WAIT -> CHECK` using `stepNo`.
- On heartbeat timeout, Cloud marks the stale session `DISCONNECTED`, records
  `AGENT_HEARTBEAT_TIMEOUT`, and marks the Agent `OFFLINE` only if no ACTIVE
  session remains.
- When an Agent becomes disconnected and no ACTIVE session remains, Cloud fails
  that Agent's in-flight commands. For `GENERATE_DOCUMENT`, Cloud publishes a
  non-retryable `DocumentRenderCommandFailedEvent` with
  `ARCHDOX_AGENT_DISCONNECTED`, and the document generation Flower flow marks
  the current document job/report failed.

Flower owns the periodic decision. It does not own WebSocket objects. The
in-memory session registry is only asked to close a local socket when the stale
session belongs to the current API process.

## Duplicate Connection Policy

The same `agentId` must not have two healthy WebSocket sessions at the same
time.

```text
same agentId HELLO
-> clean heartbeat-timed-out sessions for that agent
-> if ACTIVE session still exists, reject the new socket
-> if no ACTIVE session remains, register the new socket
```

Do not let a new process silently take over a healthy Agent identity. If a real
second runtime is needed, register a separate `agentCode`, for example
`office-backup-1` or `cloud-managed-2`.

## WebSocket Command Payload Policy

The Agent WebSocket is the control plane. It carries small command envelopes,
ACK/COMPLETE/FAIL events, heartbeat, and routing state. It must not carry large
document snapshots, full photo lists with binary data, template content, or
generated artifacts.

For document rendering, Cloud sends `GENERATE_DOCUMENT` with only the job
identity and a render package reference:

```json
{
  "documentJobId": 7001,
  "officeId": 10,
  "reportId": 1000,
  "outputFormat": "DOCX",
  "renderPackageMethod": "GET",
  "renderPackageUrl": "/agent/api/v1/document-jobs/7001/render-package",
  "resultStorageKind": "ARCHDOX_AGENT"
}
```

The ArchDox Agent resolves relative URLs against its configured Cloud HTTP base
URL, authenticates with its Agent credentials, then downloads the render package
through HTTP. The render package contains the neutral job snapshot, selected
template revision metadata, template download URL, photo metadata, and photo
download URLs.

Template bytes, working-photo bytes, original-photo bytes, generated artifacts,
and delivery uploads must move through HTTP or S3-compatible storage adapters.
They must not be embedded in WebSocket command payloads.

## Agent Failure Contract

When a command fails, the Agent reports a machine-readable failure contract:

```json
{
  "type": "FAIL",
  "commandId": 56,
  "errorCode": "TEMPLATE_INVALID_DOCX",
  "retryable": false,
  "errorMessage": "Document template content could not be read",
  "result": {
    "errorCode": "TEMPLATE_INVALID_DOCX",
    "retryable": false,
    "message": "Document template content could not be read"
  }
}
```

Flower owns the retry decision. For `GENERATE_DOCUMENT`, retryable Agent
failures re-enter the document render flow backoff path. Non-retryable Agent
failures fail the job immediately with the Agent error code.

Examples of retryable failures:

- temporary HTTP/S3/MinIO/NAS/network failures
- timeout while fetching a render package or uploading an artifact
- interrupted command execution during shutdown

Examples of non-retryable failures:

- invalid command payload
- missing required template/report data
- unreadable or corrupt DOCX template
- unsupported output format
- Agent authentication failure
- storage profile not configured
- PDF converter unavailable

## Cloud API Responsibilities

`cloud-api` owns:

- REST request/response contracts
- authentication and office isolation
- `document_jobs` state and progress
- `document_artifacts` metadata
- `document_delivery_requests` and prepared Cloud delivery metadata
- `archdox_agents` registration and authentication state
- `archdox_agent_commands` command state
- Flower orchestration and Bloom event conversion

`cloud-api` must not own office NAS paths or assume an agent is local.

When Cloud API has multiple instances, Agent command routing must follow
`docs/architecture/CLOUD_API_SCALING_AND_ROUTING.md`. API instances should not
call each other directly. Commands are persisted in `archdox_agent_commands`,
and the API instance that owns the WebSocket connection only acts as the
transport.

## Database Rules

Use these names:

- `archdox_agents`
- `archdox_agent_install_tokens`
- `archdox_agent_heartbeats`
- `archdox_agent_commands`
- `archdox_agent_sessions` for multi-API-instance routing visibility

Do not add `local_agent_*` tables or `local_agent_id` columns.

`archdox_agents` must include:

- `office_id`
- `agent_code`
- `deployment_mode`
- `status`
- `auth_mode`
- `device_secret_hash`
- `capabilities_json`
- `storage_profile_json`
- heartbeat/auth timestamps

`capabilities_json` is part of command routing, not just monitoring. An Agent
must advertise stable execution capabilities during `HELLO`, for example:

```json
{
  "documentGeneration": true,
  "documentRender": true,
  "documentArtifactDelivery": true,
  "photoPickup": true,
  "pdfExport": true,
  "outputFormats": ["DOCX", "HTML", "PDF", "DOCX_AND_PDF", "HTML_AND_PDF"]
}
```

PDF-related formats must be advertised only when the selected Agent runtime has
LibreOffice or another configured PDF converter available. If the office PC or
cloud-managed Agent does not have `soffice` installed and enabled, it must not
claim `PDF` capability.

Implemented V1:

- `ArchDoxAgentCapabilityProvider` asks the Agent document runtime to verify
  LibreOffice availability before adding PDF-related output formats.
- The verification runs `soffice --version` through the shared process runner
  and caches the result for the current executable path.
- Compose uses the service name `archdox-agent` and the Docker image under
  `infra/docker/archdox-agent`.
- The Docker `pdf-smoke-test` target exercises the real Agent command executor
  with `DOCX_AND_PDF`, stores both artifacts through `AgentDocumentStore`, and
  asserts the PDF begins with the `%PDF` signature.

## Worker And Storage Terms

- Document worker type: `ARCHDOX_AGENT` means the job is executed by the Agent
  runtime.
- Agent deployment mode decides whether that runtime is office-installed or
  cloud-managed.
- Artifact storage kind `ARCHDOX_AGENT` means the binary is agent-managed and
  Cloud has metadata only.
- Photo storage kind `AGENT_MANAGED` means the original has moved out of
  temporary Cloud storage and into the Agent storage profile.

## Naming Rules

Use:

- `ArchDoxAgent`
- `ArchDoxAgentCommand`
- `ArchDoxAgentDeploymentMode`
- `ArchDoxAgentAuthenticationService`
- `ArchDoxAgentCommandService`
- `archdox-agent`
- `archdox_agent_*`

Avoid:

- `LocalAgent`
- `local-agent`
- `local_agent_*`
- `LOCAL_AGENT`

`LOCAL_OFFICE` is allowed only as a deployment mode value.
