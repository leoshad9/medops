#!/usr/bin/env bash
# Rebuild the full MedOps stack on EC2 (used by GitHub Actions or manually).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ "$(id -u)" -eq 0 ]]; then
  echo "Do not run this as root. Exit sudo and run as ec2-user."
  echo "Root has no GitHub SSH key, and Docker files would be owned by root."
  exit 1
fi

BRANCH="${DEPLOY_BRANCH:-dev}"
BASE_COMPOSE_FILE="${BASE_COMPOSE_FILE:-docker-compose.yml}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"

# Explicit -f skips docker-compose.override.yml (local port publishing).
# sudo: this instance's ec2-user is not in the docker group yet.
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

HTTP_PORT="${HOST_HTTP_PORT:-80}"

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

HTTPS_PORT="${HOST_HTTPS_PORT:-443}"
echo "==> Waiting for health on :$HTTP_PORT / :$HTTPS_PORT"
for i in $(seq 1 90); do
  if curl -fsSk "https://127.0.0.1:${HTTPS_PORT}/actuator/health/liveness" >/dev/null 2>&1 \
    || curl -fsSk "https://127.0.0.1:${HTTPS_PORT}/actuator/health" >/dev/null 2>&1 \
    || curl -fsS "http://127.0.0.1:${HTTP_PORT}/actuator/health/liveness" >/dev/null 2>&1 \
    || curl -fsS "http://127.0.0.1:${HTTP_PORT}/actuator/health" >/dev/null 2>&1; then
    echo "Healthy after $((i * 2))s"
    "${COMPOSE[@]}" ps
    prune_unused_docker
    exit 0
  fi
  sleep 2
done

echo "Deploy finished but health check did not pass in time."
"${COMPOSE[@]}" ps
"${COMPOSE[@]}" logs --tail=100 medops-api medops-ui medops-ai || true
prune_unused_docker
exit 1
