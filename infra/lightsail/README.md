# ArchDox Lightsail Deployment

This folder contains the first lightweight single-host deployment.

Runtime topology:

- `caddy`: public HTTPS entrypoint, certificate automation, domain redirects, and direct-IP blocking.
- `web`: Nginx serving the public site, user web app, and admin app by host.
- `api-server`: Cloud API.
- `postgres`: Cloud API database.
- `minio`: S3-compatible object storage.
- `archdox-agent-personal`: cloud-managed document agent for the seeded personal test office.
- `archdox-agent-inwoo`: cloud-managed document agent for the seeded INWOO office.

Host routing:

- `archdox.co.kr`: public ArchDox Engine/MCP product site.
- `www.archdox.co.kr`: redirect to `archdox.co.kr`.
- `app.archdox.co.kr`: existing user SaaS app.
- `admin.archdox.co.kr`: office/platform admin app.
- `api.archdox.co.kr`: Cloud API boundary for external clients, mobile apps, health checks, and future Engine API users.
- `mcp.archdox.co.kr`: reserved MCP Gateway host; currently returns 404 until implemented.

All hosts can point to the same Lightsail static IP in MVP.

The test seed currently assumes:

- office `1`: personal test office for `test@test.co.kr`
- office `2`: INWOO office for `inwoo@test.co.kr`
- office `3`: personal platform-admin workspace for `vvzerg@test.co.kr`

Do not commit real `.env` files or seeded passwords.

For domain deployment, set:

- `ARCHDOX_CORS_ALLOWED_ORIGINS=https://archdox.co.kr,https://www.archdox.co.kr,https://app.archdox.co.kr,https://admin.archdox.co.kr,https://api.archdox.co.kr`
- `ARCHDOX_RATE_LIMIT_USE_FORWARDED_HEADERS=true`
- `ARCHDOX_MULTIPART_MAX_FILE_SIZE=100MB`
- `ARCHDOX_MULTIPART_MAX_REQUEST_SIZE=120MB`
- `DOCUMENT_LOCAL_ROOT=/var/lib/archdox/documents`

`ARCHDOX_RATE_LIMIT_USE_FORWARDED_HEADERS` is safe here because the API is only
reachable through the bundled Caddy/Nginx reverse proxy path.

The multipart limits must be high enough for Agent document delivery uploads
because generated DOCX/PDF files can include multiple working images.

`DOCUMENT_LOCAL_ROOT` must point to a writable persistent volume. In the
Lightsail compose file this is under `archdox-api-data` so prepared delivery
downloads survive container restarts.
