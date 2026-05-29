# ArchDox Infra

Local development starts with `docker-compose.yml` at the repository root.

Phase 0 keeps infrastructure intentionally small:
- PostgreSQL for Cloud API state
- MailHog for email development
- MinIO for S3-compatible upload flows

Production must not expose application internals directly. Put Cloudflare/WAF
and Nginx or ALB in front of the Cloud API, and expose only `80/443`.

Baseline Nginx policy lives in:

- `infra/nginx/archdox.conf`

Internal ports that must remain private:

- `8080`: Cloud API
- `18080`: ArchDox Agent
- `5432`: PostgreSQL
- `9000`, `9001`: MinIO
