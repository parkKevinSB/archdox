# archdox-agent-launcher

`archdox-agent-launcher` is the small local bootstrap/update layer for the
ArchDox Agent runtime.

It is not the document/photo execution runtime. That remains `archdox-agent`.

## Current Phase

The launcher currently performs the first safe step only:

1. Read the Cloud API runtime manifest.
2. Compare local Agent runtime version, protocol version, and launcher version.
3. Print a machine-readable update decision.
4. Return a distinct exit code.

It does not yet download, replace, restart, or roll back the runtime package.

## Exit Codes

| Code | Meaning |
| ---: | --- |
| `0` | Runtime is compatible. |
| `10` | Update is recommended. Commands may still run. |
| `20` | Update is required. Commands should not run until the runtime is updated. |
| `30` | Manifest lookup failed. |

## Example

```bash
./gradlew :archdox-agent-launcher:run --args="\
  --cloud-api-base-url https://api.archdox.co.kr \
  --channel stable \
  --platform windows-x64 \
  --agent-version 0.0.1-dev \
  --protocol-version 2026-06-25 \
  --launcher-version embedded"
```

The Cloud endpoint is:

```text
GET /api/v1/archdox-agents/runtime-manifest?channel=stable&platform=windows-x64
```

## Next Phase

The next launcher phase should add:

- signed package download
- SHA-256 verification
- runtime package staging
- atomic swap
- process restart
- rollback to previous runtime on failed restart
