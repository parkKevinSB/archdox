# archdox-agent-launcher

`archdox-agent-launcher` is the small local bootstrap/update layer for the
ArchDox Agent runtime.

It is not the document/photo execution runtime. That remains `archdox-agent`.
This module is intentionally separate from `archdox-agent`; it has no Spring
Boot runtime dependency and can manage the Agent process from the outside.

## Current Phase

The launcher currently performs the first safe step only:

1. Read the Cloud API runtime manifest.
2. Compare local Agent runtime version, protocol version, and launcher version.
3. Print a machine-readable update decision.
4. When explicitly requested, download and install a verified runtime package.
5. When explicitly requested, start the installed runtime and verify health.
6. Roll back to the previous runtime when startup health check fails.
7. Register a Windows logon scheduled task for the local user when the
   packaged PowerShell helper is used.
8. Bundle a small Java runtime image in the Windows launcher package so users
   do not need to install Java separately.
9. Return a distinct exit code.

Runtime installation is opt-in. The launcher does not silently replace the
runtime when it is only checking compatibility.
Runtime process start is also opt-in.

The launcher has its own build identity. Gradle writes
`META-INF/archdox/archdox-agent-launcher-build.properties` into the launcher
distribution, and the launcher uses that value as the default
`--launcher-version`. `embedded` is now reserved for Agent runtimes started
without the separate launcher.

## Exit Codes

| Code | Meaning |
| ---: | --- |
| `0` | Runtime is compatible. |
| `10` | Update is recommended. Commands may still run. |
| `20` | Update is required. Commands should not run until the runtime is updated. |
| `30` | Manifest lookup failed. |
| `40` | Runtime package installation failed. |
| `50` | Runtime process start or health check failed. |
| `60` | Runtime process stop failed. |

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
  --force-install \
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

To start the currently installed runtime:

```bash
./gradlew :archdox-agent-launcher:run --args="\
  --cloud-api-base-url https://api.archdox.co.kr \
  --start-runtime \
  --install-dir /opt/archdox/agent-runtime \
  --work-dir /var/lib/archdox/agent-launcher \
  --runtime-health-url http://127.0.0.1:18080/actuator/health"
```

The launcher discovers the runtime command in this order:

1. `--runtime-command`
2. `current/bin/archdox-agent` or `current/bin/archdox-agent.bat`
3. `archdox-agent*.jar` under `current`

If startup fails or the health URL does not return `UP` before
`--startup-timeout-seconds`, the launcher stops the new process and moves
`previous` back to `current` when `--rollback-on-start-failure=true`.

The Cloud endpoint is:

```text
GET /api/v1/archdox-agents/runtime-manifest?channel=stable&platform=windows-x64
```

The Cloud API build identity can also be checked through:

```text
GET /api/v1/system/version
```

The browser download UI should read:

```text
GET /api/v1/archdox-agents/launcher-manifest?channel=stable&platform=windows-x64
```

The runtime manifest includes Cloud API build metadata plus the Agent runtime
version policy: minimum, recommended, latest, protocol bounds, and launcher
version bounds. The launcher ignores unknown manifest fields so Cloud can add
observability metadata without breaking older launchers.

Cloud API does not create packages at request time. Build/release automation
creates the packages, uploads them to release storage, and configures Cloud API
with a public release base URL or explicit package URLs.

Package tasks:

```bash
./gradlew :archdox-agent-launcher:launcherPackageSha256 \
  -ParchdoxVersion=0.0.1 \
  -ParchdoxReleaseChannel=stable \
  -ParchdoxPlatform=windows-x64

./gradlew :archdox-agent:agentRuntimePackageSha256 \
  -ParchdoxVersion=0.0.1 \
  -ParchdoxReleaseChannel=stable \
  -ParchdoxPlatform=windows-x64
```

The generated files are placed under:

```text
build/archdox-releases/agent-launcher/<channel>/<platform>/<version>/
build/archdox-releases/agent-runtime/<channel>/<platform>/<version>/
```

## Local Runtime Commands

Use `--launcher-command` for local runtime control:

```bash
--launcher-command check      # default: read manifest, optionally install
--launcher-command status     # read local pid/state and health
--launcher-command start      # start current runtime and verify health
--launcher-command stop       # stop recorded runtime pid/process tree
--launcher-command restart    # stop then start
--launcher-command supervise  # keep checking health and restart when needed
```

`status` and `stop` are local-only commands. They do not call Cloud API.

The launcher writes local control files under `--work-dir`:

```text
agent.pid
launcher-state.json
logs/agent-runtime.out.log
logs/agent-runtime.err.log
```

`supervise` is a long-running command. It checks health every
`--monitor-interval-seconds` and restarts the runtime when the process is gone
or health is not confirmed. `--max-restarts 0` means no fixed restart limit.

## Windows User Auto Start

The release package includes Windows helper scripts under `windows/`.
It also includes top-level double-click helpers:

```text
Install ArchDox Agent.bat
Remove ArchDox Agent.bat
README-WINDOWS.txt
jre/
```

For a normal Windows user installation:

```text
1. Extract the zip package.
2. Double-click Install ArchDox Agent.bat.
```

The install helper:

1. Creates local runtime, work, and storage directories.
2. Runs the launcher with `--apply-update --force-install`.
3. Writes `launcher-task.json` under the launcher work directory.
4. Registers a current-user Windows scheduled task named `ArchDox Agent`.
5. Starts the task immediately.

The scheduled task runs `run-archdox-agent-supervisor.ps1`, which starts the
launcher in `supervise` mode. This means the Agent starts again after Windows
logon and is restarted when the local runtime process disappears or health
cannot be confirmed.

To remove the scheduled task:

```text
Double-click Remove ArchDox Agent.bat.
```

## Next Phase

The next launcher phase should add:

- A user-facing installer wizard and tray/settings UI.
- Windows service integration for office/server installations.
- Linux service integration.
