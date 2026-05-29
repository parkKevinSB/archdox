$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$runDir = Join-Path $repoRoot ".local-e2e"
$pidFile = Join-Path $runDir "pids.json"

if (Test-Path $pidFile) {
    $processes = Get-Content -Encoding UTF8 -Path $pidFile | ConvertFrom-Json
    foreach ($item in @($processes)) {
        try {
            $process = Get-Process -Id $item.pid -ErrorAction Stop
            Write-Host "Stopping $($item.name) pid=$($item.pid)"
            Stop-Process -Id $process.Id -Force
        } catch {
            Write-Host "Already stopped: $($item.name) pid=$($item.pid)"
        }
    }
    Remove-Item -LiteralPath $pidFile -Force
}

Write-Host "Local E2E app processes stopped. Docker dependencies are left running intentionally."
Write-Host "To stop Docker dependencies too, run: docker compose stop postgres mailhog minio"
