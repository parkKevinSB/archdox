param(
    [int]$AgentOfficeId = 1,
    [string]$AgentCode = "local-e2e-agent",
    [int]$ApiPort = 8080,
    [int]$DbPort = 55432,
    [int]$ClientPort = 5173,
    [int]$AdminPort = 5174,
    [switch]$SkipAgent,
    [switch]$SkipWeb
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$runDir = Join-Path $repoRoot ".local-e2e"
$logDir = Join-Path $runDir "logs"
$pidFile = Join-Path $runDir "pids.json"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Start-ArchDoxProcess {
    param(
        [string]$Name,
        [string]$FilePath,
        [string[]]$ArgumentList,
        [string]$WorkingDirectory
    )

    $stdout = Join-Path $logDir "$Name.out.log"
    $stderr = Join-Path $logDir "$Name.err.log"
    $process = Start-Process `
        -FilePath $FilePath `
        -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -WindowStyle Hidden `
        -PassThru

    [pscustomobject]@{
        name = $Name
        pid = $process.Id
        stdout = $stdout
        stderr = $stderr
    }
}

function Test-LocalPort {
    param([int]$Port)

    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

Write-Host "Starting Docker dependencies: postgres, mailhog, minio"
if (-not (Test-LocalPort -Port $DbPort)) {
    $postgresName = "archdox-postgres-$DbPort"
    $existingPostgres = docker ps -a --filter "name=^/$postgresName$" --format "{{.Names}}"
    if ($existingPostgres -eq $postgresName) {
        docker start $postgresName | Out-Null
    } else {
        docker run -d `
            --name $postgresName `
            -e POSTGRES_DB=archdox `
            -e POSTGRES_USER=archdox `
            -e POSTGRES_PASSWORD=archdox `
            -p "${DbPort}:5432" `
            -v "${postgresName}-data:/var/lib/postgresql/data" `
            postgres:16-alpine | Out-Null
    }
}

Push-Location $repoRoot
try {
    docker compose up -d mailhog minio minio-init
} finally {
    Pop-Location
}

$processes = @()

$env:SPRING_PROFILES_ACTIVE = "local"
$env:SERVER_PORT = "$ApiPort"
$env:DB_URL = "jdbc:postgresql://localhost:$DbPort/archdox"
$env:DB_USERNAME = "archdox"
$env:DB_PASSWORD = "archdox"
$env:ARCHDOX_AI_FAKE_PROVIDER_ENABLED = "true"
$env:ARCHDOX_AI_DEV_BOOTSTRAP_ENABLED = "true"
$env:DOCUMENT_AI_REVIEW_ENABLED = "true"
$env:PLATFORM_OPS_AI_DIAGNOSIS_ENABLED = "true"
$env:PLATFORM_OPS_AI_PROVIDER_CODE = "fake-ops"
$env:PLATFORM_OPS_AI_MODEL = "fake-ops-model"
$env:AGENT_ALLOW_SHARED_SECRET_AUTH = "true"
$env:AGENT_SHARED_SECRET = "dev-agent-secret-change-me"
$env:PLATFORM_ADMIN_BOOTSTRAP_EMAILS = "archdox-admin@test.co.kr"

$processes += Start-ArchDoxProcess `
    -Name "cloud-api" `
    -FilePath (Join-Path $repoRoot "gradlew.bat") `
    -ArgumentList @(":cloud-api:bootRun", "--no-daemon") `
    -WorkingDirectory $repoRoot

if (-not $SkipAgent) {
    $env:SPRING_PROFILES_ACTIVE = "local"
    $env:SERVER_PORT = "18080"
    $env:CLOUD_AGENT_WS_URL = "ws://localhost:$ApiPort/agent/ws"
    $env:CLOUD_API_BASE_URL = "http://localhost:$ApiPort"
    $env:AGENT_WS_ENABLED = "true"
    $env:AGENT_AUTH_MODE = "SHARED_SECRET"
    $env:AGENT_OFFICE_ID = "$AgentOfficeId"
    $env:AGENT_CODE = $AgentCode
    $env:AGENT_SHARED_SECRET = "dev-agent-secret-change-me"
    $env:AGENT_LOCAL_STORAGE_ROOT = (Join-Path $runDir "agent-storage")
    $env:DOCUMENT_EXPORT_LIBREOFFICE_ENABLED = "true"
    $env:DOCUMENT_EXPORT_LIBREOFFICE_PATH = "soffice"

    $processes += Start-ArchDoxProcess `
        -Name "archdox-agent" `
        -FilePath (Join-Path $repoRoot "gradlew.bat") `
        -ArgumentList @(":archdox-agent:bootRun", "--no-daemon") `
        -WorkingDirectory $repoRoot
}

if (-not $SkipWeb) {
    $env:VITE_API_BASE_URL = "http://localhost:$ApiPort"
    $processes += Start-ArchDoxProcess `
        -Name "client-web" `
        -FilePath "npm.cmd" `
        -ArgumentList @("run", "dev", "--", "--host", "127.0.0.1", "--port", "$ClientPort") `
        -WorkingDirectory (Join-Path $repoRoot "client\web")

    $env:VITE_API_BASE_URL = "http://localhost:$ApiPort"
    $processes += Start-ArchDoxProcess `
        -Name "admin-web" `
        -FilePath "npm.cmd" `
        -ArgumentList @("run", "dev", "--", "--host", "127.0.0.1", "--port", "$AdminPort") `
        -WorkingDirectory (Join-Path $repoRoot "admin")
}

$processes | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 -Path $pidFile

Write-Host ""
Write-Host "ArchDox local E2E environment is starting."
Write-Host "Cloud API: http://localhost:$ApiPort"
Write-Host "Client web: http://127.0.0.1:$ClientPort"
Write-Host "Admin web:  http://127.0.0.1:$AdminPort"
Write-Host "MailHog:    http://localhost:8025"
Write-Host "MinIO:      http://localhost:9001"
Write-Host "Logs:       $logDir"
Write-Host "PID file:   $pidFile"
