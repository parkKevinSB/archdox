# Image Upload And ArchDox Agent Policy

This document records the agreed image upload, storage, and ArchDox Agent
communication policy for ArchDox.

## Final Direction

ArchDox separates these concerns:

1. Upload path
2. Long-term original storage
3. Working image storage
4. Thumbnail storage
5. Cloud metadata
6. ArchDox Agent command/status communication

The most important decision is:

> Office plan originals should not be stored long-term in cloud by default.

Cloud may temporarily receive files for mediation, but the office-owned original
image should end up in the office ArchDox Agent/NAS storage.

For cloud-only operation, the same ArchDox Agent runtime is still used. The
difference is that the Agent is operated by ArchDox as `CLOUD_MANAGED` and its
durable file store must be S3-compatible object storage. A production
cloud-managed Agent must not treat the API server disk or container filesystem
as the durable photo/document store.

## ArchDox Agent Connection Model

The ArchDox Agent connects outbound to Cloud API through WebSocket.

```text
ArchDox Agent
→ Cloud API WebSocket
→ command receive / heartbeat / status report
```

This does not require a public IP for the office network because the connection
is initiated by the ArchDox Agent.

The WebSocket channel is primarily for:

- heartbeat
- command delivery
- photo pickup command
- document generation command
- progress update
- completion/failure report
- small metadata messages

The WebSocket channel is not the default file upload tunnel for large images.
Using WebSocket as a binary file tunnel is possible, but it increases API server
load and complicates retry, chunking, reconnect, timeout, memory, and rate-limit
handling.

## Recommended Office Upload Flow

The default office flow is Cloud-mediated upload plus Agent pull.

```text
Client
→ Cloud API: POST /api/v1/photos/intent
→ temporary object storage upload
→ Cloud API: POST /api/v1/photos/{photoId}/confirm
→ Cloud API sends WebSocket command to ArchDox Agent
→ ArchDox Agent downloads temporary object
→ ArchDox Agent stores original in NAS/agent storage
→ ArchDox Agent reports completion through WebSocket
→ Cloud deletes temporary original
```

This flow avoids public IP requirements and keeps large binary transfer away
from the Cloud API runtime.

## Storage Policy By File Type

### Original

- Office plan with local Agent: long-term storage in ArchDox Agent/NAS by
  default.
- Cloud: temporary only until ArchDox Agent confirms pickup.
- Personal cloud-only plan: ArchDox-managed S3-compatible storage is allowed
  because the user may not have a running local PC Agent.
- Personal plan with local Agent: original storage may be local disk, NAS, or a
  user-owned S3-compatible bucket configured through the Agent profile.
- Personal original retention may become a paid/plan option. A short retention
  window such as seven days is acceptable for cloud-only temporary originals
  when the plan does not include long-term original retention.

### Working Image

The working image is used for document generation and normal preview.

Recommended default:

- Resize long edge to a controlled size such as 1600-2048 px.
- Strip sensitive EXIF unless the field is explicitly needed.
- Store in cloud object storage when needed for cross-device editing and report
  generation.
- For office plans, long-term working image retention can be shorter than
  thumbnail/metadata retention.

### Thumbnail

The thumbnail is used for list views, wizard previews, and mobile UI.

Recommended default:

- Store in cloud object storage or CDN-backed storage.
- Use a compact format such as WebP.
- Keep small enough for frequent UI reads.

### Metadata

Cloud DB stores metadata and logical references:

- `photo_id`
- `office_id`
- `project_id`
- `site_id`
- `report_id`
- `step_code`
- `checklist_item_id`
- `site_supervision_entry_id` as a weak supervision ledger/projection link
- `trade_code` / `process_code` / `inspection_item_code`
- `caption`
- `location_note`
- `drawing_ref` as a future drawing/floor-plan logical reference
- future related issue / finding / corrective-action reference
- future drawing/floor-plan marker/coordinate, when attached
- report/document inclusion status, when selected for output
- `hash_sha256`
- `mime_type`
- `bytes`
- `width`
- `height`
- `storage_kind`
- `storage_ref`
- `thumbnail_storage_ref`
- upload/pickup status

Cloud DB must not store office NAS absolute paths such as:

```text
D:\NAS\...
\\office-nas\...
/mnt/nas/...
```

Instead, store logical keys such as:

```text
offices/10/reports/1000/photos/abc/working.jpg
```

The ArchDox Agent maps the logical key to its configured local path.

This is important for future MCP/AI-agent use. A user's local AI agent may ask
ArchDox to organize photos, documents, and reports, but ArchDox should expose
scoped evidence context and logical file references. Raw NAS/PC paths remain
inside the ArchDox Agent or user-controlled storage configuration.

## Upload Targets

### API_LOCAL

Development/MVP-only target.

```text
Client → Cloud API → local development storage
```

Use this for local development, tests, and temporary MVP smoke paths only. It is
not the production office or cloud-managed storage policy.

`API_LOCAL` means the API server process stores the bytes through its configured
local storage adapter. On a hosted server this is still server-local storage,
not AWS S3. Do not rely on it for production originals, production document
artifacts, or office retention.

### S3

Personal/cloud direct upload target.

```text
Client → S3-compatible object storage
```

Cloud API creates a photo row, returns a presigned upload URL, and stores only
metadata and object keys.

For cloud-only users, the production target should be S3-compatible object
storage. The exact provider can be AWS S3, MinIO, R2, Wasabi, Naver Object
Storage, or another compatible private object store, as long as ArchDox can
presign, read, and delete according to policy.

### CLOUD_MEDIATED

Default local-office target and the normal handoff shape when originals must
move from cloud temporary storage into an Agent-managed store.

```text
Client → temporary object storage → ArchDox Agent pull → NAS/agent storage
```

Cloud object storage is used as a temporary handoff area. The ArchDox Agent is
notified through WebSocket.

Use `CLOUD_MEDIATED` only when there is an Agent-side final store to pick into:

- `LOCAL_OFFICE` with local disk or NAS
- personal local Agent with local disk, NAS, or user-owned S3-compatible store
- cloud-managed Agent picking from temporary upload storage into its durable
  S3-compatible profile

Do not create `PHOTO_PICKUP_STUCK` incidents for cloud-only flows that do not
require Agent original pickup. Stuck pickup detection is meaningful only when
the selected upload policy says an Agent must pick the original.

### ARCHDOX_AGENT_DIRECT

Optional advanced target.

This is available only when one of these conditions is true:

1. Client is inside the same office LAN.
2. A managed tunnel is available.
3. VPN/Tailscale/Cloudflare Tunnel or equivalent private connectivity exists.

Without one of these, client-to-agent direct upload is not available because the
ArchDox Agent normally has no public inbound address.

## S3-Compatible Storage Policy

The object storage layer should be implemented through an adapter interface.

Possible providers:

- AWS S3
- MinIO
- Cloudflare R2
- Backblaze B2
- Wasabi
- Naver Object Storage
- Other S3-compatible services

The API server can run on AWS while using non-AWS S3-compatible storage, as long
as the provider supports the required features:

- object upload
- presigned upload URL or equivalent
- object download for ArchDox Agent pickup
- private bucket/object access
- lifecycle or explicit delete for temporary originals
- CORS support for browser/mobile uploads

Provider-specific behavior may differ for multipart upload, lifecycle rules,
event notification, ACLs, and CDN integration. Do not assume every
S3-compatible provider supports every AWS S3 feature.

ArchDox now has a common Cloud API storage boundary for new file-oriented work:

```text
StorageService
  -> LocalFileStorageService
  -> NasStorageService
  -> S3CompatibleStorageService
```

Photo upload still keeps its photo-specific storage adapter for upload intents,
temporary original handoff, derivative generation, and Agent pickup policy.
Future photo refactors should converge on the common `StorageService` boundary
without losing those photo-domain rules.

Office-managed S3-compatible profiles are a Cloud API control record, not a raw
Agent config dump. The profile stores encrypted credentials and safe connection
metadata. The admin UI should ask for the smallest understandable set:

- storage type
- bucket
- prefix
- region
- endpoint only for MinIO/custom providers
- access key and secret key

Cloud validates the profile by writing, reading, and deleting a small test
object. A verified profile can later be used by a cloud-managed Agent provision
or reconfiguration action. Until that actuator exists, the profile is a prepared
and tested storage target, not proof that an Agent is already using it.

## Personal External Drive Option

Personal users may later choose an external personal cloud drive for original
backup. This is an optional provider feature, not the MVP core storage path.

Potential providers:

- Google Drive
- Naver MYBOX, only if a suitable official upload API is available
- OneDrive or other provider APIs, if OAuth and upload APIs are stable

Recommended rule:

```text
Personal MVP:
ArchDox-managed S3-compatible storage for WORKING + THUMBNAIL

Personal optional original backup:
External drive provider stores ORIGINAL
ArchDox DB stores provider/file metadata
Document generation still uses WORKING
```

Do not store only a public/shared URL. Store provider metadata instead:

- `provider`
- `provider_account_id`
- `provider_file_id`
- `provider_folder_id`
- `asset_type`
- `hash_sha256`
- `bytes`
- `last_verified_at`

The API server can upload automatically only after the user authorizes the
provider through OAuth or an equivalent consent flow. Refresh tokens and provider
credentials must be treated as secrets.

External drive support must not block normal report editing or document
generation because users can revoke permission, delete/move files, hit provider
quota, or use a provider without a reliable public upload API.

## CDN Policy

Object storage and CDN are separate roles.

```text
Object storage = stores files
CDN = serves previews/downloads efficiently
```

Recommended:

- Upload to object storage using presigned upload URLs.
- Serve thumbnails and preview images through CDN or signed URLs.
- Keep originals private.
- Do not expose permanent public URLs for sensitive project photos.

## Current Implementation Status

Current code implements configurable upload storage with asset separation:

- `POST /api/v1/photos/intent`
- `PUT /api/v1/photos/{photoId}/content/{kind}`
- `POST /api/v1/photos/{photoId}/confirm`
- `POST /api/v1/photos/{photoId}/agent-pickup-complete`
- `GET /api/v1/photos?reportId=...`
- `GET /api/v1/photos/{photoId}`
- Cloud API `/agent/ws` WebSocket command channel
- Cloud API internal `/agent/api/v1/photos/{photoId}/assets/{assetType}/content`
  endpoint for API-local Agent downloads
- ArchDox Agent outbound WebSocket client with actual `PHOTO_PICKUP` execution

The code now has `photo_assets` for:

- `ORIGINAL`
- `WORKING`
- `THUMBNAIL`

The `agent-pickup-complete` endpoint remains an MVP REST fallback. The primary
office path is now the WebSocket command result: the Agent downloads the
temporary original, stores it under its configured agent storage root, reports
`COMPLETE`, and Cloud moves the original asset reference to `AGENT_MANAGED`.

Implemented upload targets:

- `API_LOCAL`: development default
- `S3`: S3-compatible presigned PUT upload
- `CLOUD_MEDIATED`: S3-compatible temporary original plus cloud working/thumbnail

Implemented office storage profile management:

- `GET /api/v1/office-ops/storage-profiles`
- `POST /api/v1/office-ops/storage-profiles`
- `POST /api/v1/office-ops/storage-profiles/{profileId}/test`
- provider types: `AWS_S3`, `MINIO`, `CUSTOM_S3`
- profile statuses: `DRAFT`, `VERIFIED`, `FAILED`, `DISABLED`
- credentials are encrypted at rest and are not returned to the UI
- connection test performs real object write/read/delete

Implemented Agent command messages:

- `HELLO`
- `WELCOME`
- `HEARTBEAT`
- `COMMAND`
- `ACK`
- `COMPLETE`
- `FAIL`

Implemented `PHOTO_PICKUP` command behavior:

- Cloud includes `downloadMethod`, `downloadUrl`, `downloadHeaders`,
  `downloadExpiresAt`, and `suggestedAgentOriginalStorageRef` in the command
  payload.
- S3-compatible temporary originals use presigned `GET` URLs.
- API-local temporary originals use the internal Agent download endpoint.
- ArchDox Agent downloads the original, stores it locally, validates SHA-256 when
  the command hash is a real SHA-256 value, and reports `COMPLETE`.
- On `COMPLETE`, Cloud deletes the temporary original when requested and updates
  the `ORIGINAL` asset to `AGENT_MANAGED`.
- On `FAIL`, Cloud publishes a command failure event. The `photo-pickup`
  Flower flow decides retry/backoff while attempts remain.
- On final failure or in-flight timeout after retry exhaustion, the
  `photo-pickup` Flower flow marks the pickup status as `FAILED`.

Implemented pickup retry/backoff:

- `PhotoUploadConfirmed` is published through Bloom after upload confirm.
- `PhotoPickupEventHandler` submits the `photo-pickup` Flower flow when the
  photo still requires office original pickup.
- The flow dispatches a fresh `PHOTO_PICKUP` command for each attempt, waits for
  ACK, then waits for completion/failure.
- Default max attempts: `5`.
- Default backoff: `30s`, `60s`, `120s`, `240s`, then capped at `300s`.
- Every Flower retry creates a new command and refreshes the `downloadUrl` and
  `downloadExpiresAt`, which is required for S3-compatible presigned download
  URLs.
- If the Agent is offline, the flow waits through the configured backoff and
  tries again until the retry budget is exhausted.

Implemented derivative generation:

- `PhotoUploadConfirmed` is published through Bloom after the upload confirm
  transaction commits.
- The `photo-derivative-generation` Flower flow runs these steps:
  1. `prepare-source`
  2. `generate-working`
  3. `generate-thumbnail`
  4. `finalize`
- `generate-working` creates a re-encoded working image with long edge capped
  at 2048px. Re-encoding strips source EXIF metadata.
- `generate-thumbnail` creates a WebP thumbnail with long edge capped at 512px.
- `prepare-source`, `generate-working`, and `generate-thumbnail` are
  asynchronous Flower-style steps. They submit work to the photo derivative
  executor, return `stay()`, and later observe completion, timeout, or retry
  backoff through `stepNo`.
- Derivative step state uses:
  - `stepNo=0`: submit task
  - `stepNo=10`: wait for task completion or timeout
  - `stepNo=20`: retry backoff wait
- Retry policy is configured by `PHOTO_DERIVATIVE_MAX_ATTEMPTS`,
  `PHOTO_DERIVATIVE_RETRY_BASE_DELAY_MS`,
  `PHOTO_DERIVATIVE_RETRY_MAX_DELAY_MS`, and
  `PHOTO_DERIVATIVE_STEP_TIMEOUT_MS`.
- Generated asset bytes are stored through the configured storage adapter
  (`API_LOCAL` or S3-compatible storage), then `photo_assets` metadata is
  updated with bytes, width, height, hash, and uploaded status.
- The flow publishes `PhotoDerivativesGenerated` or
  `PhotoDerivativeGenerationFailed`. Failure events include the failed
  derivative `stepId`, exhausted `attempt`, and failure reason.

ArchDox Agent runtime settings:

- `CLOUD_AGENT_WS_URL`: WebSocket command channel URL.
- `CLOUD_API_BASE_URL`: base URL used to resolve relative Agent download URLs.
- `AGENT_LOCAL_STORAGE_ROOT`: local/NAS root where logical photo keys are
  materialized.
- `AGENT_INSTALL_TOKEN`: one-time token used for first pairing.
- `AGENT_ID` and `AGENT_DEVICE_SECRET`: credentials used after pairing.
- `AGENT_SHARED_SECRET`: disabled-by-default development fallback for WebSocket
  and Agent download requests when shared-secret auth is explicitly allowed.
- `PHOTO_PICKUP_MAX_ATTEMPTS`: retry budget for photo pickup.
- `PHOTO_PICKUP_RETRY_BASE_DELAY_MS`: first retry delay.
- `PHOTO_PICKUP_RETRY_MAX_DELAY_MS`: maximum backoff delay.
- `PHOTO_PICKUP_STEP_TIMEOUT_MS`: ACK/completion wait timeout per command
  attempt.
- `PHOTO_PICKUP_WORKER_INTERVAL_MS`: Flower worker tick interval.

Next implementation targets:

1. Add mTLS or equivalent managed device certificates on top of device
   credentials when deployment packaging is ready.
2. Add optional personal external-drive provider metadata model.

## Recommended Operating Defaults

```text
Development:
API_LOCAL

Personal cloud-only plan:
Shared cloud-managed Agent pool + S3-compatible object storage
Working image + thumbnail in object storage
Original retention by plan, often temporary by default

Personal with local Agent:
LOCAL_OFFICE deployment mode owned by the personal workspace
LOCAL_FILE/NAS/user-owned S3-compatible storage

Office with local runtime:
CLOUD_MEDIATED by default
Temporary cloud original only until ArchDox Agent pickup
Long-term original in NAS/agent storage
Cloud keeps metadata + thumbnail + optional working image

Office cloud-managed:
Cloud-managed Agent + verified office S3-compatible storage profile
No durable API_LOCAL/container-local storage

Advanced office option:
ARCHDOX_AGENT_DIRECT only with LAN/tunnel/VPN connectivity
```
