# Git Workflow

ArchDox uses `master` as the always-buildable baseline branch.

## Branches

Use short, purpose-shaped branch names:

```text
feature/report-workflow-definition
fix/report-revision-permission
phase/7-1-report-workflow-v1
docs/deployment-portability
```

Recommended prefixes:

- `feature/`: product or platform feature
- `fix/`: bug fix
- `phase/`: larger planned phase
- `docs/`: documentation-only change
- `ci/`: CI, build, or repository operation change

## Commit Rules

Commit messages should describe the product or platform change:

```text
Add GitHub Actions CI foundation
Fix report revision permission check
Add deployment portability storage boundary
```

Avoid vague messages such as:

```text
update
fix
work
```

## Pull Request Rules

Small changes may be committed directly when explicitly requested. For normal
development, prefer a branch and PR.

Every PR should explain:

- what changed
- how it was verified
- known limitations or follow-up work

## CI Rules

GitHub Actions must stay green on `master`.

Current CI checks:

- backend compile
- backend tests
- `client/web` build
- `admin` build
- Docker Compose config validation

Backend CI builds the Flower/Bloom runtime snapshots that are vendored under:

```text
libs/bloom
libs/flower
```

If Flower/Bloom are updated from their source repositories, sync the runtime
snapshot deliberately and run the full backend test suite before committing.

## Do Not Commit

Never commit:

- `.env` or `.env.*` secrets
- build outputs
- logs
- IDE metadata
- local document binaries used only for testing
- generated storage files
