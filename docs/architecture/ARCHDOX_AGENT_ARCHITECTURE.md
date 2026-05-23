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
    Future storage adapter boundary: LOCAL_FS, NAS, S3_COMPATIBLE
  config
    deploymentMode, Cloud API endpoint, credentials, storage profile
```

Current code keeps command executors under `cloud` because the MVP is small.
When the package grows, command executors should move to a dedicated
`command` package without changing the protocol.

## Storage Profile

Agent storage must be configuration-driven. Cloud must not infer storage from
the agent name.

```yaml
archdox:
  agent:
    deployment-mode: LOCAL_OFFICE
    storage:
      original:
        kind: LOCAL_FS
        root-path: D:/ArchDox/original
      working:
        kind: LOCAL_FS
        root-path: D:/ArchDox/working
      artifact:
        kind: LOCAL_FS
        root-path: D:/ArchDox/artifacts
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
