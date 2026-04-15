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
    docker-compose up -d
    exit 0
}

$env:GIT_COMMIT = (git rev-parse HEAD 2>$null) ?? "unknown"
Write-Host "🔨 Building EverLoad — commit: $env:GIT_COMMIT"

docker-compose build
docker-compose up -d

Write-Host "✅ Done. Deployed commit: $env:GIT_COMMIT"