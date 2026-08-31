#!/usr/bin/env bash
# Rebuild the full MedOps stack on EC2 (used by GitHub Actions or manually).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BRANCH="${DEPLOY_BRANCH:-dev}"
BASE_COMPOSE_FILE="${BASE_COMPOSE_FILE:-docker-compose.yml}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"

# Explicit -f skips docker-compose.override.yml (local port publishing).
COMPOSE=(docker compose -f "$BASE_COMPOSE_FILE" -f "$COMPOSE_FILE" --env-file "$ENV_FILE")

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE in $ROOT_DIR"
  echo "Create it from .env.example before deploying (do not commit secrets)."
  exit 1
fi

# shellcheck disable=SC1090
set -a
source "$ENV_FILE"
set +a

HTTP_PORT="${HOST_HTTP_PORT:-80}"

for file in "$BASE_COMPOSE_FILE" "$COMPOSE_FILE"; do
  if [[ ! -f "$file" ]]; then
    echo "Missing $file in $ROOT_DIR"
    exit 1
  fi
done

echo "==> Syncing git branch $BRANCH"
git fetch --prune origin
git checkout "$BRANCH"
git reset --hard "origin/$BRANCH"

echo "==> Building and starting stack"
"${COMPOSE[@]}" up -d --build --remove-orphans

echo "==> Waiting for health on :$HTTP_PORT"
for i in $(seq 1 90); do
  if curl -fsS "http://127.0.0.1:${HTTP_PORT}/actuator/health/liveness" >/dev/null 2>&1 \
    || curl -fsS "http://127.0.0.1:${HTTP_PORT}/actuator/health" >/dev/null 2>&1; then
    echo "Healthy after $((i * 2))s"
    "${COMPOSE[@]}" ps
    exit 0
  fi
  sleep 2
done

echo "Deploy finished but health check did not pass in time."
"${COMPOSE[@]}" ps
"${COMPOSE[@]}" logs --tail=100 medops-api medops-ui medops-ai || true
exit 1
