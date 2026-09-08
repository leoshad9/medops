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

# ---------------------------------------------------------------------------
# Corruption helpers
# ---------------------------------------------------------------------------
is_corrupt_pull() {
  local out="$1"
  echo "$out" | grep -qiE 'crc32 mismatch|corrupted|invalid compressed|failed to extract|blob not found|sha256 mismatch|checksum mismatch'
}

repair_corrupt_blob() {
  local digest="$1" found=0 store
  # containerd natively-managed blobs
  store="/var/lib/containerd/io.containerd.content.v1.content/blobs/sha256"
  if [ -d "$store" ]; then
    local file="$store/${digest:0:2}/$digest"
    if [ -f "$file" ]; then
      echo "==> removing corrupt blob from containerd: $digest"
      sudo rm -f "$file"
      found=1
    fi
  fi
  # classic docker store fallback (depends on Docker storage driver)
  for candidate in \
    "/var/lib/docker/image/overlay2/layerdb/sha256/$digest" \
    "/var/lib/docker/image/overlay2/layerci/$digest" \
    "/var/lib/docker/overlay2/${digest:0:2}/${digest}/link" ; do
    if [ -f "$candidate" ]; then
      echo "==> removing corrupt layer metadata: $candidate"
      sudo rm -f "$candidate"
      found=1
    fi
  done
  return $found
}

repair_layer_metadata() {
  local img="$1" tag="$2" digest
  echo "==> resolving digest for $img:$tag"
  digest=$(sudo docker pull --quiet "$img:$tag" 2>/dev/null | tail -1 | tr -d '[:space:]' || true)
  if [ -z "$digest" ] || [ "${#digest}" -lt 32 ]; then
    # fall back to inspect
    digest=$(sudo docker inspect --format '{{index .RepoDigests 0}}' "$img:$tag" 2>/dev/null | cut -d@ -f2 || true)
  fi
  if [ -n "$digest" ] && [ "${#digest}" -ge 32 ]; then
    repair_corrupt_blob "$digest"
  fi
}

ensure_image() {
  local img="$1" tag="$2" attempt out digest
  tag="${tag:-latest}"
  for attempt in 1 2 3 4; do
    out=$(sudo docker pull "$img:$tag" 2>&1)
    if sudo docker inspect "$img:$tag" >/dev/null 2>&1; then
      echo "==> $img:$tag ready"
      return 0
    fi
    echo "==> attempt $attempt failed for $img:$tag"
    if is_corrupt_pull "$out"; then
      echo "==> corruption detected — attempting repair"
      repair_layer_metadata "$img" "$tag"
      sudo docker system prune -f >/dev/null 2>&1 || true
      sleep 3
      if sudo docker pull "$img:$tag" >/dev/null 2>&1 && sudo docker inspect "$img:$tag" >/dev/null 2>&1; then
        echo "==> $img:$tag recovered after repair"
        return 0
      fi
    fi
    sudo docker system prune -f >/dev/null 2>&1 || true
    sleep 5
  done
  echo "==> [ERROR] could not pull $img:$tag after retries"
  return 1
}

pull_base_images() {
  local f img
  for f in $(find . -maxdepth 2 -name Dockerfile | sort); do
    while IFS= read -r img; do
      [ -n "$img" ] || continue
      pull_with_retry "$img" || return 1
    done < <(grep -hE '^[[:space:]]*FROM[[:space:]]' "$f" \
             | awk '$1=="FROM"{if ($2 ~ /^--/) print $3; else print $2}' | sort -u)
  done
  return 0
}

# Wraps the entire deploy in a retry so transient failures restart cleanly.
run_deploy() {
  echo "==> Syncing git branch $BRANCH"
  if git fetch --prune origin; then
    git checkout "$BRANCH"
    git reset --hard "origin/$BRANCH"
  else
    local origin_url https_url
    origin_url="$(git remote get-url origin 2>/dev/null || true)"
    if [[ "$origin_url" == git@github.com:* ]]; then
      https_url="https://github.com/${origin_url#git@github.com:}"
      echo "==> SSH fetch failed; retrying via $https_url"
      GIT_TERMINAL_PROMPT=0 git fetch --prune "$https_url" "+refs/heads/${BRANCH}:refs/remotes/origin/${BRANCH}" && {
        git checkout "$BRANCH"
        git reset --hard "origin/$BRANCH"
      } || return 1
    else
      return 1
    fi
  fi

  echo "==> Pre-pulling base images"
  if ! pull_base_images; then
    return 1
  fi

  echo "==> Pruning unused Docker data before build (volumes kept)"
  sudo docker container prune -f || true
  sudo docker image prune -af || true
  sudo docker builder prune -af || true

  echo "==> Building and starting stack"
  build_ok=0
  for attempt in 1 2 3; do
    if "${COMPOSE[@]}" up -d --build --remove-orphans; then
      build_ok=1
      break
    fi
    echo "==> [WARN] docker compose up failed (attempt $attempt) -- logs:"
    "${COMPOSE[@]}" logs --tail=50 || true
    if echo "$( "${COMPOSE[@]}" logs --tail=100 2>&1 )" | grep -qiE 'crc32 mismatch|corrupted|invalid compressed|failed to extract|blob not found'; then
      echo "==> corruption detected — pruning and retrying"
      sudo docker system prune -af >/dev/null 2>&1 || true
      sleep 5
      continue
    fi
    sudo docker system prune -f >/dev/null 2>&1 || true
    sleep 5
  done
  return $(( 1 - build_ok ))
}

# Repeatedly run the full deploy until success or exhaustion.
MAX_ATTEMPTS="${DEPLOY_MAX_ATTEMPTS:-2}"
attempt=1
while true; do
  echo ""
  echo "=========================================="
  echo "==> DEPLOY ATTEMPT $attempt OF $MAX_ATTEMPTS"
  echo "=========================================="
  if run_deploy; then
    echo ""
    echo "==> Deploy successful"
    "${COMPOSE[@]}" ps
    echo "==> Pruning unused Docker data (volumes kept)"
    sudo docker container prune -f || true
    sudo docker image prune -af || true
    sudo docker builder prune -af || true
    exit 0
  fi
  attempt=$(( attempt + 1 ))
  if [[ "$attempt" -gt "$MAX_ATTEMPTS" ]]; then
    echo ""
    echo "==> [ERROR] deploy failed after $MAX_ATTEMPTS attempts"
    "${COMPOSE[@]}" logs --tail=50 || true
    exit 1
  fi
  echo "==> Restarting from scratch in 10s..."
  sleep 10
done
