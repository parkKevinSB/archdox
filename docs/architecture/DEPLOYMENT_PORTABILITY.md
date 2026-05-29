# Deployment Portability

ArchDox must run in more than one operating shape. The application code should
not assume AWS, public IP networking, AWS S3, or one specific AI provider.

## Supported Operating Shapes

### AWS Hosted

```text
external users
-> public domain / load balancer
-> Cloud API + WebSocket server on AWS
-> S3-compatible object storage

ArchDox Agent
-> wss://public-api-domain/agent/ws
```

This is the default SaaS-style deployment. React apps may be served through a
static hosting/CDN layer, while API/WebSocket traffic goes to Cloud API
instances.

### Local Server / Tailscale

```text
authorized devices
-> Tailscale private address or MagicDNS
-> Cloud API + WebSocket server on office/home server PC
-> MinIO, NAS, or mounted local filesystem

ArchDox Agent
-> ws://archdox-server.tailnet-name.ts.net/agent/ws
```

This mode is for office/private operation without a public IP. Tailscale limits
network reachability, but it does not replace ArchDox authentication. User auth,
Agent install tokens, device secrets, and later mTLS still apply.

## Code Boundaries

Business code must depend on ArchDox ports, not vendor names:

- `StorageService`
- `AiModelGateway`
- Agent WebSocket URL properties
- Spring profiles and environment variables

Do not put `if aws`, `if tailscale`, `if nas`, or hardcoded cloud URLs inside
domain/application logic.

## Implementation Status

The portability foundation is intentionally small. It is not a full deployment
platform yet, but the first boundaries now exist in code.

Cloud API storage boundary:

- `com.archdox.cloud.storage.application.StorageService`
- `LocalFileStorageService`
- `NasStorageService`
- `S3CompatibleStorageService`
- `StorageServiceResolver`
- `StorageObjectRef`
- `StorageUploadUrl`
- `StorageProperties`

Cloud API AI boundary:

- `archdox-ai-harness` owns ArchDox document QA, report preflight, and ops
  diagnosis prompts, schemas, and finding extraction.
- `flower-ai-harness-core` owns the AI harness flow contract:
  `AiModelGateway`, prompt rendering, output validation, retry/refine, and
  finding emission.
- `flower-ai-harness-spring-ai` adapts Spring AI `ChatClient` to
  `AiModelGateway`.
- `flower-ai-harness-spring-boot-starter` auto-registers the Spring AI backed
  `AiModelGateway` when a `ChatClient` bean is available.
- Cloud API owns only ArchDox run/finding persistence, operation events, and
  REST APIs.
- If harness observation, trace export, or provider health monitoring becomes
  reusable outside ArchDox, those parts may later be extracted into
  `flower-ai-harness-*` modules. Until then they stay ArchDox-specific.

Shared document export boundary:

- `com.archdox.document.DocumentArtifactExporter`
- `LibreOfficeDocumentArtifactExporter`
- `LibreOfficePdfExportOptions`
- `LibreOfficeCommandRunner`

ArchDox Agent registers the LibreOffice PDF exporter from the runtime property
prefix:

```text
archdox.documents.export.libre-office.enabled
archdox.documents.export.libre-office.executable-path
archdox.documents.export.libre-office.timeout-ms
```

This keeps PDF conversion portable:

- AWS/cloud containers can install LibreOffice and Korean fonts.
- Local/Tailscale servers can point to a local `soffice` executable.
- Office ArchDox Agent can use locally installed office/conversion tooling when
  the office PC is the better place to render final artifacts.
- Business code still requests `OutputFormat.PDF` or `DOCX_AND_PDF`; it does
  not know where LibreOffice is installed.
- Cloud API does not host LibreOffice conversion as an in-process document
  generation fallback.

ArchDox does not auto-install LibreOffice from application code. Deployment is
responsible for installing the converter and fonts in the selected runtime:

- Windows/local: install LibreOffice and set `DOCUMENT_EXPORT_LIBREOFFICE_PATH`
  if `soffice` is not on `PATH`.
- Docker/cloud: bake LibreOffice and Korean fonts into the document-worker or
  ArchDox Agent image.
- Dev/default: leave `DOCUMENT_EXPORT_LIBREOFFICE_ENABLED=false`; PDF requests
  should fail fast unless a capable Agent is connected.

Runtime profile files:

- `cloud-api/src/main/resources/application-dev.yml`
- `cloud-api/src/main/resources/application-aws.yml`
- `cloud-api/src/main/resources/application-local.yml`
- `archdox-agent/src/main/resources/application-dev.yml`
- `archdox-agent/src/main/resources/application-aws.yml`
- `archdox-agent/src/main/resources/application-local.yml`

Docker Compose local operation:

- PostgreSQL for persistence
- MailHog for development email
- MinIO for S3-compatible object storage
- optional Cloud API and ArchDox Agent containers through the `app` profile
- optional Ollama through the `ai` profile

## Storage Boundary

Cloud API now has a common storage port:

```text
StorageService
  -> LocalFileStorageService
  -> NasStorageService
  -> S3CompatibleStorageService
```

S3-compatible includes AWS S3, MinIO, Wasabi, Cloudflare R2, Naver Object
Storage, and similar providers when their S3 API behavior is compatible.

Database records should store logical metadata:

```text
fileId
storageType
bucketName
objectKey
originalFileName
contentType
size
hash when available
```

Do not store absolute NAS or local filesystem paths in business tables. If the
root path changes, only deployment configuration should change.

Current photo storage still has a photo-specific adapter because upload intent,
temporary original handoff, and derivative generation have photo-domain rules.
New storage work should move toward the common `StorageService` boundary instead
of creating more vendor-specific application code.

## ArchDox Agent Storage Profiles

The ArchDox Agent has its own runtime storage profile because it may run beside
office files, on a cloud VM, or inside Docker.

Supported profile kinds:

- `LOCAL_FILE`: local disk or mounted drive on the Agent machine.
- `NAS`: office network share or mapped NAS drive.
- `S3_COMPATIBLE`: object storage such as AWS S3, MinIO, Wasabi, R2, or Naver
  Object Storage.

Phase 9-1 and Phase 9-2 implement the Agent storage foundation:

- `LOCAL_FILE` and `NAS` share the same logical-ref-to-root-path resolver.
- `S3_COMPATIBLE` uses the Agent S3-compatible adapter for MinIO/AWS
  S3/Wasabi/R2-style object storage.
- The public profile sent to Cloud must never expose local/NAS absolute paths.
  Cloud sees `kind`, `fileSystemBacked`, `rootConfigured`, and optional
  `bucket`/`prefix`; it stores only logical artifact/photo refs.

Office PC without NAS:

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

Office NAS:

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

Cloud-managed Agent:

```yaml
archdox:
  agent:
    deployment-mode: CLOUD_MANAGED
    storage:
      artifact:
        kind: S3_COMPATIBLE
        bucket: archdox-artifacts
        prefix: agents/cloud-1
```

## AI Boundary

ArchDox must not own provider-specific AI clients. AI provider integration is
delegated to Spring AI and the flower-ai-harness Spring adapter:

```text
DocumentAiReviewService
-> AiModelGateway
-> flower-ai-harness-spring-ai
-> Spring AI ChatClient
-> OpenAI or Ollama
```

Cloud API stores ArchDox-specific state such as `document_ai_review_runs`,
`document_ai_review_findings`, `document_jobs`, and `operation_events`.
OpenAI/Ollama HTTP details, provider SDKs, and provider model clients belong to
Spring AI or a future harness provider module, not to ArchDox business code.

Runtime selection uses Spring AI properties:

```yaml
spring:
  ai:
    model:
      chat: none | openai | ollama
```

Cloud/API defaults keep AI disabled with `none`. `application-local.yml` selects
`ollama` unless overridden, and `application-aws.yml` selects `openai` unless
overridden. Set `SPRING_AI_MODEL_CHAT=none` to run without AI review.

## Profiles

Cloud API profile files:

- `application-dev.yml`: local developer defaults, local files, AI disabled
- `application-aws.yml`: S3-compatible storage, Spring AI OpenAI-ready, shared Agent
  secret auth disabled by default
- `application-local.yml`: local server defaults, MinIO/S3-compatible storage,
  Spring AI Ollama-ready

ArchDox Agent profile files:

- `application-dev.yml`: disabled WebSocket by default
- `application-aws.yml`: public `wss://.../agent/ws` style connection
- `application-local.yml`: local/Tailscale-style `ws://.../agent/ws`
  connection and local filesystem/NAS-like storage roots

All secrets must come from environment variables or secret stores, not committed
YAML.

## WebSocket Endpoint Rules

The Agent WebSocket endpoint is a runtime setting. Code must never assume that
Cloud API is always on AWS or always on a public domain.

Examples:

```text
AWS:
CLOUD_AGENT_WS_URL=wss://api.example.com/agent/ws

Local server on LAN:
CLOUD_AGENT_WS_URL=ws://192.168.0.10:8080/agent/ws

Local server through Tailscale:
CLOUD_AGENT_WS_URL=ws://archdox-server.tailnet-name.ts.net:8080/agent/ws
```

Tailscale changes network reachability only. It does not remove the need for
ArchDox user authentication, Agent device credentials, install tokens, or future
mTLS/device certificates.

## Operational Decision Rules

Use these rules when choosing a deployment shape:

- AWS hosted mode is the default when many outside users need public access.
- Local server/Tailscale mode is acceptable when the operator controls all
  devices that access the service.
- S3-compatible storage is the default abstraction for cloud/object storage,
  whether the provider is AWS S3, MinIO, Wasabi, R2, or another compatible
  provider.
- NAS/local filesystem storage is allowed only behind `StorageService`; business
  tables must store logical references, not absolute paths.
- AI providers are optional. Features must tolerate `spring.ai.model.chat=none`
  unless a specific feature explicitly requires AI.
- React clients and mobile clients should call the same Cloud API contract. They
  must not know whether files ultimately live in AWS S3, MinIO, NAS, or local
  disk.

## Docker Compose Local Operation

`docker-compose.yml` supports:

- `postgres`
- `mailhog`
- `minio`
- `minio-init`
- `api-server` with profile `app`
- `archdox-agent` with profile `app`
- `ollama` with profile `ai`

Examples:

```bash
docker compose up -d
docker compose --profile app up -d
docker compose --profile app --profile ai up -d
```

The ArchDox Agent service is present but `AGENT_WS_ENABLED` defaults to `false` in
Compose so it does not accidentally connect with incomplete office credentials.
Enable it with explicit Agent credentials when testing a real office.

The Compose `archdox-agent` service uses
`infra/docker/archdox-agent/Dockerfile`. That image bakes in:

- Java 21 runtime
- LibreOffice Writer/headless conversion support
- Noto CJK fonts for Korean document rendering
- `/var/lib/archdox-agent` as the default Agent storage root

The image enables LibreOffice export by default:

```text
DOCUMENT_EXPORT_LIBREOFFICE_ENABLED=true
DOCUMENT_EXPORT_LIBREOFFICE_PATH=soffice
```

This is a deployment/runtime concern. Application source code still talks to the
portable `DocumentArtifactExporter` boundary, and PDF routing still depends on
the selected runtime advertising a verified `pdfExport` capability.

PDF smoke verification:

```powershell
.\scripts\smoke\archdox-agent-pdf-smoke.ps1
```

The smoke script runs a Docker target that executes
`DocumentRenderCommandExecutorPdfSmokeTest` with
`-Darchdox.agent.pdf-smoke.enabled=true`. Normal `gradlew test` runs keep this
test skipped so developer machines and CI do not need LibreOffice installed
unless the smoke target is explicitly requested.
