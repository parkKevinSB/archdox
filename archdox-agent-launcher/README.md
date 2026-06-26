# archdox-agent-launcher

`archdox-agent-launcher` is the small local bootstrap/update layer for the
ArchDox Agent runtime.

It is not the document/photo execution runtime. That remains `archdox-agent`.

## Current Phase

The launcher currently performs the first safe step only:

1. Read the Cloud API runtime manifest.
2. Compare local Agent runtime version, protocol version, and launcher version.
3. Print a machine-readable update decision.
4. When explicitly requested, download and install a verified runtime package.
5. Return a distinct exit code.

Runtime installation is opt-in. The launcher does not silently replace the
runtime when it is only checking compatibility.

## Exit Codes

| Code | Meaning |
| ---: | --- |
| `0` | Runtime is compatible. |
| `10` | Update is recommended. Commands may still run. |
| `20` | Update is required. Commands should not run until the runtime is updated. |
| `30` | Manifest lookup failed. |
| `40` | Runtime package installation failed. |

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

To install a downloadable update:

```bash
./gradlew :archdox-agent-launcher:run --args="\
  --cloud-api-base-url https://api.archdox.co.kr \
  --apply-update \
  --install-dir /opt/archdox/agent-runtime \
  --work-dir /var/lib/archdox/agent-launcher"
```

The install directory is managed by the launcher:

```text
agent-runtime/
  current/   active runtime package
  previous/  last runtime package before replacement
```

The launcher verifies SHA-256 before replacement. If `signatureUrl` is present
in the manifest, the launcher also requires
`--signature-public-key-path <public-key.pem>` and verifies an Ed25519
signature before installing the package.

The Cloud endpoint is:

```text
GET /api/v1/archdox-agents/runtime-manifest?channel=stable&platform=windows-x64
```

## Next Phase

The next launcher phase should add:

- process restart
- runtime process supervision
- rollback to previous runtime on failed runtime start
