# ArchDox Infra

Local development starts with `docker-compose.yml` at the repository root.

Phase 0 keeps infrastructure intentionally small:
- PostgreSQL for Cloud API state
- MailHog for email development
- MinIO for S3-compatible upload flows
