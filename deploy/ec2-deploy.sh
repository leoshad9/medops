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
# sudo: ssm-user is not in the docker group, so docker commands use sudo.
COMPOSE=(sudo docker compose -f "$BASE_COMPOSE_FILE" -f "$COMPOSE_FILE" --env-file "$ENV_FILE")

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE in $ROOT_DIR"
  echo "Create it from .env.example before deploying (do not commit secrets)."
  exit 1
fi

# shellcheck disable=SC1090
set -a
source "$ENV_FILE"
set +a

for file in "$BASE_COMPOSE_FILE" "$COMPOSE_FILE"; do
  if [[ ! -f "$file" ]]; then
    echo "Missing $file in $ROOT_DIR"
    exit 1
  fi
done

sync_git() {
  echo "==> Syncing git branch $BRANCH"
  if git fetch --prune origin; then
    git checkout "$BRANCH"
    git reset --hard "origin/$BRANCH"
    return
  fi

  local origin_url https_url
  origin_url="$(git remote get-url origin)"
  if [[ "$origin_url" == git@github.com:* ]]; then
    https_url="https://github.com/${origin_url#git@github.com:}"
    echo "==> SSH fetch failed; retrying via $https_url"
    GIT_TERMINAL_PROMPT=0 git fetch --prune "$https_url" "+refs/heads/${BRANCH}:refs/remotes/origin/${BRANCH}"
    git checkout "$BRANCH"
    git reset --hard "origin/$BRANCH"
    return
  fi

  echo "git fetch failed. Set SKIP_GIT_SYNC=true to deploy the files already on disk."
  exit 1
}

if [[ "${SKIP_GIT_SYNC:-}" == "true" ]]; then
  echo "==> Skipping git sync (SKIP_GIT_SYNC=true)"
else
  sync_git
fi

echo "==> Pruning unused Docker data before build (volumes kept)"
sudo docker container prune -f || true
sudo docker image prune -af || true
sudo docker builder prune -af || true

echo "==> Building and starting stack"
"${COMPOSE[@]}" up -d --build --remove-orphans

# Drop stopped containers, unused images, and build cache from previous deploys.
# Never pass --volumes: postgres_data and clinical_files must stay.
prune_unused_docker() {
  echo "==> Pruning unused Docker data (volumes kept)"
  sudo docker container prune -f || true
  sudo docker image prune -af || true
  sudo docker builder prune -af || true
}

echo "==> Verifying all containers are healthy"
for i in $(seq 1 30); do
  UNHEALTHY=$("${COMPOSE[@]}" ps --format json 2>/dev/null \
    | grep -c '"Health":"unhealthy"' || true)
  NOT_RUNNING=$("${COMPOSE[@]}" ps --format json 2>/dev/null \
    | grep -c '"State":"exited"' || true)
  if [[ "$UNHEALTHY" -eq 0 && "$NOT_RUNNING" -eq 0 ]]; then
    echo "All containers healthy after $((i * 5))s"
    "${COMPOSE[@]}" ps
    prune_unused_docker
    exit 0
  fi
  sleep 5
done

echo "Deploy finished but some containers are not healthy."
"${COMPOSE[@]}" ps
"${COMPOSE[@]}" logs --tail=100 medops-api medops-ui medops-ai || true
prune_unused_docker
exit 1
