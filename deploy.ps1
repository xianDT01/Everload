# deploy.ps1 — Build and deploy EverLoad with the current git commit embedded.
#
# Usage:
#   .\deploy.ps1          — full rebuild + restart
#   .\deploy.ps1 --up     — restart only (no rebuild)
#
param([string]$Action = "")

Set-Location $PSScriptRoot

if ($Action -eq "--up") {
    Write-Host "🚀 Restarting containers (no rebuild)..."
    docker compose up -d --no-deps --no-build --wait --wait-timeout 180 everload
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose failed to start EverLoad (exit code $LASTEXITCODE)."
    }
    exit 0
}

$env:GIT_COMMIT = git rev-parse HEAD 2>$null
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($env:GIT_COMMIT)) {
    $env:GIT_COMMIT = "unknown"
}
$env:GIT_BRANCH = git branch --show-current
if ([string]::IsNullOrWhiteSpace($env:GIT_BRANCH)) { $env:GIT_BRANCH = "main" }
$sourceChanges = git status --porcelain --untracked-files=normal -- . ':!Caddyfile'
if ($sourceChanges) { $env:GIT_COMMIT += "-dirty" }
Write-Host "🔨 Building EverLoad — commit: $env:GIT_COMMIT"

docker compose build everload
if ($LASTEXITCODE -ne 0) {
    throw "EverLoad image build failed (exit code $LASTEXITCODE); deployment was not started."
}

docker compose up -d --no-deps --no-build --wait --wait-timeout 180 everload
if ($LASTEXITCODE -ne 0) {
    throw "Docker Compose failed to start EverLoad (exit code $LASTEXITCODE)."
}

Write-Host "✅ Done. Deployed commit: $env:GIT_COMMIT"
