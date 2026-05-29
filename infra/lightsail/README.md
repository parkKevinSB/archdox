# ArchDox Lightsail Deployment

This folder contains the first lightweight single-host deployment.

Runtime topology:

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
