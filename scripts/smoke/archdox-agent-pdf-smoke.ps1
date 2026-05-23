$ErrorActionPreference = "Stop"

Push-Location (Resolve-Path "$PSScriptRoot\..\..")
try {
    docker compose --profile app build archdox-agent
    docker run --rm --entrypoint soffice archdox/archdox-agent:local --version
    docker build `
        -f infra/docker/archdox-agent/Dockerfile `
        --target pdf-smoke-test `
        -t archdox/archdox-agent:pdf-smoke `
        .
}
finally {
    Pop-Location
}
