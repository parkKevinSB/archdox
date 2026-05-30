# ArchDox Lightsail Deployment

This folder contains the first lightweight single-host deployment.

Runtime topology:

- `caddy`: public HTTPS entrypoint, certificate automation, domain redirects, and direct-IP blocking.
- `web`: Nginx serving the user web app at `/`, admin app at `/admin/`, and proxying API/WebSocket traffic.
- `api-server`: Cloud API.
- `postgres`: Cloud API database.
- `minio`: S3-compatible object storage.
- `archdox-agent-personal`: cloud-managed document agent for the seeded personal test office.
- `archdox-agent-inwoo`: cloud-managed document agent for the seeded INWOO office.

The test seed currently assumes:

- office `1`: personal test office for `test@test.co.kr`
- office `2`: INWOO office for `inwoo@test.co.kr`
- office `3`: personal platform-admin workspace for `vvzerg@test.co.kr`

Do not commit real `.env` files or seeded passwords.

For domain deployment, set:

- `ARCHDOX_CORS_ALLOWED_ORIGINS=https://archdox.co.kr,https://www.archdox.co.kr`
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
