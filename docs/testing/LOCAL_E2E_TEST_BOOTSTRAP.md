# Local E2E Test Bootstrap

This is the local test entrypoint before AWS deployment.

## What It Starts

`scripts/local-e2e/start-local-e2e.ps1` starts:

- Docker dependencies: PostgreSQL, MailHog, MinIO
- Cloud API with `local` profile
- ArchDox Agent connected by WebSocket
- Client web on `http://127.0.0.1:5173`
- Admin web on `http://127.0.0.1:5174`

The default photo upload path is `CLOUD_MEDIATED`, so local testing exercises:

```text
client -> Cloud API/MinIO temporary original -> ArchDox Agent pickup
```

Derivatives are generated before Agent pickup deletes the temporary original.
To test simpler API-local uploads, start with `-PhotoUploadTarget API_LOCAL`.

Development fake AI is enabled by default:

- `fake-review` for report/document review flow
- `fake-ops` for platform ops diagnosis flow

This means local UI testing exercises the real Flower + AI Harness flow without
using a paid AI API key.

## Start

```powershell
.\scripts\local-e2e\start-local-e2e.ps1 -AgentOfficeId 1
```

If a port is already in use, the script stops before launching partial services.
Run the stop script first, stop the stale process, or pass another port such as
`-AgentPort 18081`.

If the test office id is not `1`, pass the office id shown in the user/admin UI.
The script uses local PostgreSQL on `localhost:55432` by default and creates or
starts an `archdox-postgres-55432` container when that port is not already open.
Use `-DbPort <port>` only when intentionally pointing at another local database.

## Stop

```powershell
.\scripts\local-e2e\stop-local-e2e.ps1
```

The stop script leaves Docker dependencies running intentionally, so database
state remains available for another run. It also clears the default local app
ports `8080`, `18080`, `5173`, and `5174` because Gradle/npm may leave child
listener processes behind.

## Test Accounts

Development database commonly uses:

```text
normal user:     new-user@test.co.kr / password-1234
platform admin:  archdox-admin@test.co.kr / password-1234
```

If the database was recreated, create or restore those accounts first.

## User Flow To Test

1. Open `http://127.0.0.1:5173`.
2. Log in as the normal user.
3. Select or create a project.
4. Select or create a site.
5. Create a report.
6. Assign 담당자 if needed.
7. Fill report steps and checklist.
8. Upload photos where the checklist asks for evidence.
9. Submit the report.
10. Open the document tab.
11. Run 생성 전 검토.
12. Confirm that code validation and fake AI validation are shown separately.
13. Sign or skip signature.
14. Generate HTML/DOCX/PDF.
15. Preview HTML and download generated artifacts.

## Platform Admin Flow To Test

1. Open `http://127.0.0.1:5174`.
2. Log in as platform admin.
3. Open 플랫폼 운영.
4. Check user, office, agent, command, document job, delivery, and event counts.
5. Run 멈춤 감지.
6. If an incident exists, run 진단.
7. Confirm the diagnosis detail panel shows deterministic snapshot and AI status.
8. Open AI 관리.
9. Confirm `fake-review` and `fake-ops` are marked as development fake providers.
10. Confirm usage/cost/log tables are readable in Korean.

## Rules

- Cloud API must not render documents inline.
- ArchDox Agent must be connected for document generation E2E.
- WebSocket is the control/event channel only.
- Artifacts and large files move through HTTP/storage.
- Fake AI is only for local/dev testing.
