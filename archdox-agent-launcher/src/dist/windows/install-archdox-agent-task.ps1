param(
    [string]$CloudApiBaseUrl = "https://api.archdox.co.kr",
    [string]$InstallDir = "$env:LOCALAPPDATA\ArchDox\agent-runtime",
    [string]$WorkDir = "$env:LOCALAPPDATA\ArchDox\agent-launcher",
    [string]$LocalStorageRoot = "$env:USERPROFILE\Documents\ArchDox",
    [string]$RuntimeHealthUrl = "http://127.0.0.1:18080/actuator/health",
    [string]$TaskName = "ArchDox Agent",
    [int]$MonitorIntervalSeconds = 15,
    [int]$MaxRestarts = 0,
    [string]$AgentInstallToken = "",
    [string]$AgentId = "",
    [string]$AgentDeviceSecret = "",
    [switch]$RunNow
)

$ErrorActionPreference = "Stop"

$distributionRoot = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")
$launcher = Join-Path $distributionRoot "bin\archdox-agent-launcher.bat"
$runner = Join-Path $PSScriptRoot "run-archdox-agent-supervisor.ps1"

if (-not (Test-Path -LiteralPath $launcher)) {
    throw "ArchDox Agent launcher was not found: $launcher"
}
if (-not (Test-Path -LiteralPath $runner)) {
    throw "ArchDox Agent task runner was not found: $runner"
}

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
New-Item -ItemType Directory -Force -Path $LocalStorageRoot | Out-Null

$configPath = Join-Path $WorkDir "launcher-task.json"
$config = [ordered]@{
    cloudApiBaseUrl = $CloudApiBaseUrl
    installDir = $InstallDir
    workDir = $WorkDir
    localStorageRoot = $LocalStorageRoot
    runtimeHealthUrl = $RuntimeHealthUrl
    monitorIntervalSeconds = $MonitorIntervalSeconds
    maxRestarts = $MaxRestarts
    agentInstallToken = $AgentInstallToken
    agentId = $AgentId
    agentDeviceSecret = $AgentDeviceSecret
}
$config | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $configPath -Encoding UTF8

Write-Host "Installing ArchDox Agent runtime..."
& $launcher `
    --cloud-api-base-url $CloudApiBaseUrl `
    --apply-update `
    --force-install `
    --install-dir $InstallDir `
    --work-dir $WorkDir

if ($LASTEXITCODE -ne 0) {
    throw "ArchDox Agent runtime installation failed with exit code $LASTEXITCODE."
}

$arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$runner`" -ConfigPath `"$configPath`""
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $arguments
$trigger = New-ScheduledTaskTrigger -AtLogOn
$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries `
    -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 1)

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Description "Starts and supervises the local ArchDox Agent after Windows logon." `
    -Force | Out-Null

Write-Host "ArchDox Agent task registered: $TaskName"
Write-Host "Config: $configPath"

if ($RunNow) {
    Start-ScheduledTask -TaskName $TaskName
    Write-Host "ArchDox Agent task started."
}
