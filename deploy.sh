#!/bin/bash
# deploy.sh — Build and deploy EverLoad with the current git commit embedded.
#
# Usage:
#   ./deploy.sh          — full rebuild + restart
#   ./deploy.sh --up     — restart only (no rebuild)
#
set -e

cd "$(dirname "$0")"

if [ "$1" = "--up" ]; then
  echo "🚀 Restarting containers (no rebuild)..."
  docker-compose up -d
  exit 0
fi

export GIT_COMMIT=$(git rev-parse HEAD 2>/dev/null || echo "unknown")
echo "🔨 Building EverLoad — commit: $GIT_COMMIT"

docker-compose build
docker-compose up -d

echo "✅ Done. Deployed commit: $GIT_COMMIT"