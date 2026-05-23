# ArchDox

[![CI](https://github.com/parkKevinSB/archdox/actions/workflows/ci.yml/badge.svg)](https://github.com/parkKevinSB/archdox/actions/workflows/ci.yml)

ArchDox is a document workflow orchestration platform for architecture office
inspection, supervision, photo handling, document generation, and operations.

The current repository contains:

- `cloud-api`: Spring Boot Cloud API, tenancy, REST APIs, orchestration, storage,
  document jobs, Agent command routing.
- `archdox-agent`: ArchDox Agent runtime for office/local or cloud-managed
  execution.
- `document-engine`: reusable document generation primitives.
- `domain-shared`: small shared domain enums and values.
- `client/web`: user-facing React app.
- `admin`: operations/admin React app.
- `docs`: architecture and development rules.
- `infra`: infrastructure notes.

## Local Prerequisites

- Java 21
- Node.js 22 or newer
- Docker Desktop

The Gradle build includes the ArchDox runtime snapshots of Flower/Bloom under:

```text
libs/bloom/
libs/flower/
```

## Local Infrastructure

Start PostgreSQL, MailHog, and MinIO:

```powershell
docker compose up -d postgres mailhog minio minio-init
```

Build the ArchDox Agent runtime image with LibreOffice and Korean/CJK fonts:

```powershell
docker compose --profile app build archdox-agent
docker run --rm --entrypoint soffice archdox/archdox-agent:local --version
```

Start the optional Cloud API and ArchDox Agent app containers:

```powershell
docker compose --profile app up -d
```

Validate Docker Compose:

```powershell
docker compose config --quiet
```

## Backend Verification

```powershell
.\gradlew.bat :cloud-api:compileJava :archdox-agent:compileJava
.\gradlew.bat test
```

Run Cloud API locally:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
.\gradlew.bat :cloud-api:bootRun
```

Health check:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

## Frontend Verification

User web app:

```powershell
cd client\web
npm ci
npm run build
```

Admin app:

```powershell
cd admin
npm ci
npm run build
```

## GitHub Actions

CI runs on pushes and pull requests targeting `master`.

Checks:

- backend compile
- backend tests
- `client/web` build
- `admin` build
- Docker Compose config validation

The backend CI builds the vendored Flower/Bloom runtime snapshots from `libs/`,
so GitHub Actions only needs access to this repository.

## Development Rules

Before changing major architecture, read:

- `AGENTS.md`
- `docs/development/AGENT_RULES.md`
- `docs/development/DDD_EVENT_ORCHESTRATION_RULES.md`
- `docs/architecture/DEPLOYMENT_PORTABILITY.md`
- `docs/architecture/FRONTEND_ARCHITECTURE.md`
