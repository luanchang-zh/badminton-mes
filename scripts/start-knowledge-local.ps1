param([switch]$Build)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if (-not (Test-Path -LiteralPath '.env.knowledge.local')) { throw 'Configure .env.knowledge.local and lightrag-service/.env first. See docs/knowledge-ui.md.' }
    if ($Build -or -not (Test-Path -LiteralPath '.codex_tmp/backend.jar')) {
        New-Item -ItemType Directory -Force '.codex_tmp' | Out-Null
        tar -cf .codex_tmp/backend-source.tar -C backend-java src build.gradle settings.gradle
        if ($LASTEXITCODE -ne 0) { throw 'Source packaging failed.' }
        $artifactDirectory = Join-Path $projectRoot '.codex_tmp'
        docker run --rm --mount "type=bind,source=$artifactDirectory,target=/output" -v badminton-knowledge-gradle:/home/gradle/.gradle --entrypoint sh gradle:9.3.1-jdk21 -c 'mkdir -p /native; tar -xf /output/backend-source.tar -C /native; cd /native; gradle --no-daemon clean test bootJar && cp build/libs/*-1.0.0.jar /output/backend.jar'
        if ($LASTEXITCODE -ne 0) { throw 'Backend build or tests failed.' }
    }
    if ($Build -or -not (Test-Path -LiteralPath 'frontend-web/dist/index.html')) {
        Push-Location frontend-web
        try {
            if (-not (Test-Path -LiteralPath node_modules)) { npm ci }
            npm run build
            if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
        } finally { Pop-Location }
    }
    docker compose --env-file .env.knowledge.local -p badminton-knowledge -f compose.knowledge-local.yml up -d
    if ($LASTEXITCODE -ne 0) { throw 'Container startup failed.' }
    $ragDirectory = Join-Path $projectRoot 'lightrag-service'
    $pythonPath = Join-Path $ragDirectory '.venv/Scripts/python.exe'
    if (-not (Test-Path -LiteralPath $pythonPath)) { throw 'Create lightrag-service/.venv and install requirements-local.txt first.' }
    $ready = $false
    try { $ready = (Invoke-RestMethod 'http://127.0.0.1:9622/health' -TimeoutSec 2).status -eq 'healthy' } catch {}
    if (-not $ready) {
        Start-Process -FilePath $pythonPath -ArgumentList '-m','tenant.server','--config','config/tenants-local.yaml','--port','9622' -WorkingDirectory $ragDirectory -WindowStyle Hidden -RedirectStandardOutput (Join-Path $ragDirectory 'local-server.log') -RedirectStandardError (Join-Path $ragDirectory 'local-server-error.log')
    }
    Write-Host 'Badminton MES: http://127.0.0.1:18082/knowledge/documents'
} finally { Pop-Location }
