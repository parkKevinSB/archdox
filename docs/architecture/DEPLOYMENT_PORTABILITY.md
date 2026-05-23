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
- `AiTextGenerationService`
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

- `com.archdox.cloud.ai.application.AiTextGenerationService`
- `OpenAiTextGenerationService`
- `OllamaTextGenerationService`
- `AiTextGenerationServiceResolver`
- `AiGenerationProperties`

Shared document export boundary:

- `com.archdox.document.DocumentArtifactExporter`
- `LibreOfficeDocumentArtifactExporter`
- `LibreOfficePdfExportOptions`
- `LibreOfficeCommandRunner`

Cloud API and ArchDox Agent both register the LibreOffice PDF exporter from the
same runtime property prefix:

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

## AI Boundary

Cloud API now has a text-generation port:

```text
AiTextGenerationService
  -> OpenAiTextGenerationService
  -> OllamaTextGenerationService
```

Use OpenAI for hosted/cloud operation and Ollama for local/private operation
when a local model is available. AI features must call the port/resolver, not a
provider-specific client directly.

## Profiles

Cloud API profile files:

- `application-dev.yml`: local developer defaults, local files, AI disabled
- `application-aws.yml`: S3-compatible storage, OpenAI-ready, shared Agent
  secret auth disabled by default
- `application-local.yml`: local server defaults, MinIO/S3-compatible storage,
  optional Ollama

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
- AI providers are optional. Features must tolerate `DISABLED` AI mode unless a
  specific feature explicitly requires AI.
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
