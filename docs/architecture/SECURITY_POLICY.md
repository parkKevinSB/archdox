# ArchDox Security Policy

ArchDox must be designed as an internet-facing system that is continuously
attacked. The default assumption is that public endpoints will receive bot,
credential stuffing, upload abuse, WebSocket connection abuse, and expensive
document-generation abuse.

## Layered Boundary

Production traffic must pass through these layers:

1. Cloudflare or equivalent WAF
2. Nginx, ALB, or another reverse proxy
3. Cloud API early security filters
4. Application authentication, authorization, office membership, and quota
5. Flower workflow/job submission
6. Agent command routing and storage access

The Cloud API, database, MinIO/NAS, and ArchDox Agent service ports must not be
directly exposed to the public internet.

## Public Ports

Allowed public ports:

- `80`: HTTP, only for redirecting to HTTPS or certificate challenge
- `443`: HTTPS

Forbidden public ports:

- `8080`: Cloud API internal port
- `18080`: ArchDox Agent internal port
- `5432`: PostgreSQL
- `9000`, `9001`: MinIO
- any internal admin, metrics, or storage port

## Current Application Guard

## Browser CORS

Cloud API must explicitly allow browser origins. Local development allows only
the client/admin dev origins:

- `http://localhost:5173`
- `http://127.0.0.1:5173`
- `http://localhost:5174`
- `http://127.0.0.1:5174`

Production must set `ARCHDOX_CORS_ALLOWED_ORIGINS` to the deployed frontend
domains. Do not use wildcard origins for authenticated API calls.

The Cloud API includes an early in-memory rate limit filter. It runs before JWT
authorization and before office membership checks, so over-limit requests are
rejected before application logic can reach the database.

Protected groups:

- `auth-login`: `POST /api/v1/auth/login`
- `auth-signup`: `POST /api/v1/auth/signup`
- `auth-refresh`: `POST /api/v1/auth/refresh`
- `platform-admin`: `/api/v1/platform-admin/**`
- `office-ops`: `/api/v1/office-ops/**`, `/api/v1/operation-events/**`
- `agent-ws`: `/agent/ws`
- `agent-api`: `/agent/api/**`
- `upload`: photo/template/content upload paths
- `document-generation`: document generation request paths
- `api`: remaining `/api/v1/**` paths

The filter returns:

- HTTP `429`
- `Retry-After`
- `X-RateLimit-Limit`
- `X-RateLimit-Remaining`
- `X-RateLimit-Reset`
- API error code `RATE_LIMITED`

This is not a substitute for Cloudflare or Nginx. It is the last application
guard before DB-backed code.

## Login Failure Lockout

Requests that pass the early rate limit still go through login failure
protection before password verification.

The login guard tracks:

- normalized email hash
- client IP hash
- failure count
- first/last failure timestamp
- temporary lock expiration

Default policy:

- email lock: 5 failed attempts within 15 minutes
- IP lock: 25 failed attempts within 15 minutes
- lock duration: 15 minutes

The system returns HTTP `429` with code `TOO_MANY_REQUESTS` when a login guard is
locked. Login errors remain generic so attackers cannot distinguish unknown
email from wrong password.

The following operation events are recorded:

- `AUTH_LOGIN_FAILED`
- `AUTH_LOGIN_LOCKED`
- `AUTH_LOGIN_BLOCKED`
- `AUTH_LOGIN_SUCCEEDED`

Events store hashed identifiers rather than raw IP/password data. These events
are visible to platform operators through operation-event and platform-ops read
APIs.

## Runtime Configuration

Default local values are intentionally moderate. Production should tune these
with environment variables:

- `ARCHDOX_RATE_LIMIT_ENABLED`
- `ARCHDOX_RATE_LIMIT_USE_FORWARDED_HEADERS`
- `ARCHDOX_RATE_LIMIT_LOGIN_MAX_REQUESTS`
- `ARCHDOX_RATE_LIMIT_SIGNUP_MAX_REQUESTS`
- `ARCHDOX_RATE_LIMIT_PLATFORM_ADMIN_MAX_REQUESTS`
- `ARCHDOX_RATE_LIMIT_OFFICE_OPS_MAX_REQUESTS`
- `ARCHDOX_RATE_LIMIT_AGENT_WS_MAX_REQUESTS`
- `ARCHDOX_RATE_LIMIT_AGENT_API_MAX_REQUESTS`
- `ARCHDOX_RATE_LIMIT_UPLOAD_MAX_REQUESTS`
- `ARCHDOX_RATE_LIMIT_DOCUMENT_GENERATION_MAX_REQUESTS`
- `ARCHDOX_RATE_LIMIT_API_MAX_REQUESTS`
- `ARCHDOX_LOGIN_PROTECTION_ENABLED`
- `ARCHDOX_LOGIN_MAX_FAILURES_PER_EMAIL`
- `ARCHDOX_LOGIN_MAX_FAILURES_PER_IP`
- `ARCHDOX_LOGIN_FAILURE_WINDOW`
- `ARCHDOX_LOGIN_LOCK_DURATION`

`ARCHDOX_RATE_LIMIT_USE_FORWARDED_HEADERS=true` is only allowed when the Cloud
API is behind a trusted reverse proxy that overwrites `CF-Connecting-IP`,
`X-Real-IP`, or `X-Forwarded-For`. Do not enable it when the API is directly
reachable from the internet.

## Admin Boundary

There are two admin concepts:

- Office admin: manages one office.
- Platform admin: operates the whole ArchDox platform.

Platform admin APIs are cross-tenant and must remain separate:

- `/api/v1/platform-admin/**`

Production platform admin access should be additionally protected by one of:

- Cloudflare Access
- VPN/Tailscale-only access
- Nginx basic auth as a temporary guard
- future platform-admin MFA

The UI being hidden is not security. The API must enforce platform-admin
authorization, and the reverse proxy should reduce brute-force traffic before it
reaches the API.

## Agent Boundary

ArchDox Agent uses WebSocket only as the control/event channel. Registered
Agents authenticate by install token first, then device secret. Shared-secret
Agent auth is disabled by default and must remain disabled in production.

Agent endpoints:

- `/agent/ws`
- `/agent/api/**`

These are public only because office Agents need to call back to the Cloud API.
They still require rate limits, heartbeat timeout handling, duplicate connection
policy, and registered Agent credentials.

## AI Credential Boundary

Provider API keys are platform secrets and must stay inside Cloud API or a
future dedicated AI proxy/worker. The MVP credential delivery mode is
`PROXY_ONLY`; Agents receive only effective AI policy metadata over WebSocket.
They must not receive OpenAI/Gemini/Claude provider API keys. The full design is
kept in `docs/architecture/AI_PROVIDER_POLICY.md`.

## Queue and Flower Submission

Expensive work must not be submitted until all checks pass:

1. request rate limit
2. JWT or Agent credential validation
3. office membership/platform role validation
4. resource ownership check
5. quota/concurrency check
6. Flower flow or command enqueue

Document generation, photo pickup, and delivery flows must not be triggered by
anonymous traffic.

## Still Required Before Public AWS Operation

- Nginx reverse proxy applied in production
- HTTPS certificate
- Cloudflare/WAF in front of Lightsail or EC2
- email verification before full account activation
- document-generation quota by user and office
- upload MIME/size enforcement at proxy and API level
- platform-admin audit events for all read/write operations
- secrets managed outside Git and outside frontend bundles
