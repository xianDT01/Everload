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
  docker compose up -d --no-deps --no-build --wait --wait-timeout 180 everload
  exit 0
fi

export GIT_COMMIT=$(git rev-parse HEAD 2>/dev/null || echo "unknown")
export GIT_BRANCH=$(git branch --show-current)
if [ -z "$GIT_BRANCH" ]; then export GIT_BRANCH=main; fi
if [ -n "$(git status --porcelain --untracked-files=normal -- . ':!Caddyfile')" ]; then
  export GIT_COMMIT="$GIT_COMMIT-dirty"
fi
echo "🔨 Building EverLoad — commit: $GIT_COMMIT"

docker compose build everload
docker compose up -d --no-deps --no-build --wait --wait-timeout 180 everload

echo "✅ Done. Deployed commit: $GIT_COMMIT"
