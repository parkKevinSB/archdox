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

Backend CI requires Flower/Bloom sibling repositories because `settings.gradle.kts`
includes:

```text
../bloom
../flower
```

If those sibling repositories are private, configure the GitHub repository secret
`ARCHDOX_CI_REPO_TOKEN` with read access to `archdox`, `bloom`, and `flower`.

## Do Not Commit

Never commit:

- `.env` or `.env.*` secrets
- build outputs
- logs
- IDE metadata
- local document binaries used only for testing
- generated storage files
