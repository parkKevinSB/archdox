param(
    [string]$ConfigPath = "$env:LOCALAPPDATA\ArchDox\agent-launcher\launcher-task.json"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $ConfigPath)) {
    throw "ArchDox Agent launcher config was not found: $ConfigPath"
}

$config = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
$distributionRoot = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")
$launcher = Join-Path $distributionRoot "bin\archdox-agent-launcher.bat"
$bundledJavaHome = Join-Path $distributionRoot "jre"

if (-not (Test-Path -LiteralPath $launcher)) {
    throw "ArchDox Agent launcher was not found: $launcher"
}
if (Test-Path -LiteralPath (Join-Path $bundledJavaHome "bin\java.exe")) {
    $env:JAVA_HOME = $bundledJavaHome
    $env:PATH = (Join-Path $bundledJavaHome "bin") + ";" + $env:PATH
}

if ($config.agentInstallToken) {
    $env:AGENT_INSTALL_TOKEN = [string]$config.agentInstallToken
}
if ($config.agentId) {
    $env:AGENT_ID = [string]$config.agentId
}
if ($config.agentDeviceSecret) {
    $env:AGENT_DEVICE_SECRET = [string]$config.agentDeviceSecret
}
if ($config.localStorageRoot) {
    $env:AGENT_LOCAL_STORAGE_ROOT = [string]$config.localStorageRoot
}

& $launcher `
    --cloud-api-base-url ([string]$config.cloudApiBaseUrl) `
    --launcher-command supervise `
    --install-dir ([string]$config.installDir) `
    --work-dir ([string]$config.workDir) `
    --runtime-health-url ([string]$config.runtimeHealthUrl) `
    --monitor-interval-seconds ([string]$config.monitorIntervalSeconds) `
    --max-restarts ([string]$config.maxRestarts)

exit $LASTEXITCODE
