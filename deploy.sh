#!/bin/bash
# MedOps Deployment Script
# Local development: start / stop / restart / status / pull
#
# Usage:
#   ./deploy.sh                     # clean start: stop + prune + pre-pull + build + run
#   ./deploy.sh start
#   ./deploy.sh stop                # stop all services (keeps data volumes)
#   ./deploy.sh stop -v             # stop and also remove data volumes (full wipe)
#   ./deploy.sh restart
#   ./deploy.sh status
#   ./deploy.sh pull                # pre-pull all base images (corruption-safe)
#   ./deploy.sh pull python:3.14-slim
#   ./deploy.sh help
#
# Resilience: detects Docker-Desktop layer corruption (crc32 mismatch / blob not
# found), re-fetches the correct blob from the Docker Hub registry API, verifies
# its sha256, places it into the containerd content store and retries.

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Generic retry helper for transient failures (health checks, prune, etc.)
# Usage: retry [max_attempts] [delay_seconds] command...
retry() {
    local max="${DEPLOY_RETRY_MAX_ATTEMPTS:-${1:-3}}"
    local delay="${DEPLOY_RETRY_DELAY:-3}"
    shift 2
    local attempt=1
    until "$@"; do
        if [ "$attempt" -ge "$max" ]; then
            echo "  - command failed after $attempt attempts" >&2
            return 1
        fi
        echo "  - attempt $attempt/$max failed, retrying in $delay s..." >&2
        sleep "$delay"
        attempt=$((attempt + 1))
    done
}

banner() {
    echo "=========================================="
    echo "  MedOps Deploy - $1"
    echo "=========================================="
    echo ""
}

check_daemon() {
    if ! docker version --format ok >/dev/null 2>&1; then
        echo "[FAIL] Docker daemon is not reachable."
        echo "  Windows: start Docker Desktop and wait until it reports running."
        echo "  Linux:   sudo systemctl start docker"
        exit 1
    fi
}

compose() {
    docker compose -f docker-compose.yml -f docker-compose.prod.yml "$@"
}

get_repo_name() {
    local img="$1"
    case "$img" in
        *.*/*) echo "" ;;                    # non-Docker-Hub registry
        */*)  echo "${img%%:*}" ;;           # namespace/repo
        *)    echo "library/${img%%:*}" ;;   # official image
    esac
}

repair_blob() {
    local img="$1" hex="$2"
    local repo
    repo="$(get_repo_name "$img")"
    if [ -z "$repo" ]; then
        echo "  - $img is not from Docker Hub - cannot auto-repair"
        return 1
    fi
    local tmp="${TMPDIR:-/tmp}/medops-blob-$hex"
    echo "  - fetching blob $hex from docker.io/$repo ..."
    local tok
    tok=$(curl -s "https://auth.docker.io/token?service=registry.docker.io&scope=repository:$repo:pull" \
        | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
    if [ -z "$tok" ]; then
        echo "  - could not obtain registry token"
        return 1
    fi
    if ! curl -sL -H "Authorization: Bearer $tok" -o "$tmp" \
         "https://registry-1.docker.io/v2/$repo/blobs/sha256:$hex"; then
        echo "  - blob download failed"
        return 1
    fi
    local actual
    actual=$(sha256sum "$tmp" 2>/dev/null | cut -d' ' -f1)
    if [ "$actual" != "$hex" ]; then
        echo "  - downloaded blob digest mismatch - discarding"
        rm -f "$tmp"
        return 1
    fi
    echo "  - blob verified, placing into containerd content store..."
    MSYS_NO_PATHCONV=1 docker run --rm -i \
        -v /var/lib/desktop-containerd/daemon/io.containerd.content.v1.content/blobs/sha256:/blobs \
        alpine sh -c "cat > /blobs/$hex" < "$tmp" 2>/dev/null || true
    MSYS_NO_PATHCONV=1 docker run --rm -i \
        -v /var/lib/containerd/io.containerd.content.v1.content/blobs/sha256:/blobs \
        alpine sh -c "cat > /blobs/$hex" < "$tmp" 2>/dev/null || true
    rm -f "$tmp"
    echo "  - blob placed"
    return 0
}

pull_with_repair() {
    local img="$1" i out rc hex
    for i in 1 2 3; do
        echo "  - pulling $img (attempt $i/3)..."
        out=$(docker pull "$img" 2>&1)
        rc=$?
        if [ $rc -eq 0 ]; then echo "  [OK] $img"; return 0; fi
        if echo "$out" | grep -qiE 'crc32 mismatch|corrupted|invalid compressed data|failed to extract layer|blob not found'; then
            echo "  [WARN] corrupt/missing layer detected for $img"
            for hex in $(echo "$out" | grep -oE 'sha256:[0-9a-f]{64}' | cut -d: -f2 | sort -u); do
                repair_blob "$img" "$hex"
            done
            docker builder prune -af >/dev/null 2>&1 || true
        else
            echo "  [WARN] pull failed: $(echo "$out" | tail -1)"
            sleep 5
        fi
    done
    echo "  [FAIL] could not pull $img after 3 attempts"
    return 1
}

get_base_images() {
    find "$SCRIPT_DIR" -maxdepth 2 -name Dockerfile 2>/dev/null \
        | xargs grep -hE '^[[:space:]]*FROM[[:space:]]' 2>/dev/null \
        | awk '$1=="FROM"{if ($2 ~ /^--/) print $3; else print $2}' \
        | grep -v '^scratch$' | sort -u
}

pull_base_images() {
    local img failed=0
    for img in $(get_base_images); do
        pull_with_repair "$img" || failed=1
    done
    return $failed
}

do_stop() {
    local wipe="${1:-no}"
    local extra=""
    [ "$wipe" = "yes" ] && extra="-v"

    banner "Stop"
    echo "[1/1] Stopping all services..."
    compose down $extra --remove-orphans 2>/dev/null || true
    echo "[OK] Services stopped"
    echo ""
}

do_start() {
    check_daemon
    banner "Clean Start (stop -> prune -> pre-pull -> build -> run)"

    echo "[0/6] Stopping any existing services..."
    compose down -v --remove-orphans 2>/dev/null || true
    echo "[OK] Existing services stopped"
    echo ""

    echo "[1/6] Pruning Docker system (containers, images, build cache)..."
    docker system prune -a -f --volumes
    echo "[OK] Docker system pruned"
    echo ""

    echo "[2/6] Removing remaining data volumes..."
    compose down -v --remove-orphans 2>/dev/null || true
    echo "[OK] Cleanup complete"
    echo ""

    echo "[3/6] Setting up certificates..."
    mkdir -p opt/medops-acme opt/medops-certs
    if [ -f "opt/medops-certs/fullchain.pem" ] && [ -f "opt/medops-certs/privkey.pem" ]; then
        echo "  - Copying certificates to Docker VM..."
        docker run --rm -v /opt/medops-certs:/target -v "${SCRIPT_DIR}/opt/medops-certs:/source:ro" alpine sh -c 'cp -r /source/* /target/' 2>/dev/null \
            || echo "  - Could not copy into Docker VM (continuing)"
    fi
    echo "[OK] Certificate directories ready"
    echo ""

    echo "[4/6] Pre-pulling base images (corruption-safe)..."
    if pull_base_images; then echo "[OK] Base images ready"; else echo "[WARN] Some base images failed to pull - build may still recover"; fi
    echo ""

    echo "[5/6] Building and starting all services..."
    local up_out rc build_ok=0 attempt
    for attempt in 1 2; do
        up_out=$(compose --env-file .env up -d --build 2>&1)
        rc=$?
        [ $rc -eq 0 ] && { build_ok=1; break; }
        echo "$up_out"
        if echo "$up_out" | grep -qiE 'crc32 mismatch|corrupted|failed to extract layer|blob not found'; then
            echo "[WARN] corruption detected during build - repairing and retrying..."
            for hex in $(echo "$up_out" | grep -oE 'sha256:[0-9a-f]{64}' | cut -d: -f2 | sort -u); do
                local img
                for img in $(get_base_images); do repair_blob "$img" "$hex" && break; done
            done
            docker builder prune -af >/dev/null 2>&1 || true
        else
            echo "[FAIL] build failed - see output above"
            break
        fi
    done
    if [ "$build_ok" != 1 ]; then show_status; return; fi
    echo "[OK] Services starting..."
    echo ""

    echo "[6/6] Waiting for services to be healthy..."
    sleep 15
    show_status
}

do_restart() {
    do_stop "no"
    do_start
}

# Full restart-on-failure loop: if the deploy fails, stop and retry from scratch.
do_start_with_retry() {
    local attempt=1
    local max_attempts="${DEPLOY_MAX_ATTEMPTS:-2}"

    while true; do
        echo ""
        echo "=========================================="
        echo "==> DEPLOY ATTEMPT $attempt OF $max_attempts"
        echo "=========================================="
        do_start
        local rc=$?
        if [ "$rc" -eq 0 ]; then
            echo ""
            echo "==> Deploy succeeded on attempt $attempt"
            return 0
        fi
        attempt=$(( attempt + 1 ))
        if [ "$attempt" -gt "$max_attempts" ]; then
            echo ""
            echo "==> [FAIL] deploy failed after $max_attempts attempts"
            return 1
        fi
        echo "==> Restarting from scratch in 10s..."
        do_stop "no" 2>/dev/null || true
        sleep 10
    done
}

show_status() {
    echo ""
    echo "=========================================="
    echo "  Service Status"
    echo "=========================================="
    compose ps
    echo ""

    RUNNING=$(compose ps --quiet | wc -l | tr -d ' ')
    TOTAL=$(compose config --services | wc -l | tr -d ' ')

    if [ "$RUNNING" -eq "$TOTAL" ] && [ "$TOTAL" -gt 0 ]; then
        echo "[SUCCESS] All $TOTAL services are running!"
    else
        echo "[WARN] $RUNNING/$TOTAL services running. Check logs with:"
        echo "   docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f"
    fi
    echo ""

    echo "Health checks:"
    retry 5 3 curl -sf -o /dev/null --max-time 5 http://localhost:8000/health 2>/dev/null && echo "  - AI  :8000  HTTP 200" || echo "  - AI  :8000  HTTP $(curl -s -o /dev/null --max-time 5 -w '%{http_code}' http://localhost:8000/health 2>/dev/null || echo '000')"
    retry 5 3 curl -sf -o /dev/null --max-time 5 http://localhost:8080/actuator/health 2>/dev/null && echo "  - API :8080  HTTP 200" || echo "  - API :8080  HTTP $(curl -s -o /dev/null --max-time 5 -w '%{http_code}' http://localhost:8080/actuator/health 2>/dev/null || echo '000')"
    retry 5 3 curl -sfk -o /dev/null --max-time 5 https://localhost/ 2>/dev/null && echo "  - UI  :443   HTTP 200" || echo "  - UI  :443   HTTP $(curl -sk -o /dev/null --max-time 5 -w '%{http_code}' https://localhost/ 2>/dev/null || echo '000')"
    echo ""

    echo "Access points:"
    echo "  - UI (HTTP):  http://localhost"
    echo "  - UI (HTTPS): https://localhost"
    echo "  - AI Service: http://localhost:8000/health"
    echo "  - API:        http://localhost:8080/actuator/health"
    echo ""
}

show_help() {
    echo "MedOps Deployment Script"
    echo ""
    echo "Usage: ./deploy.sh [command] [image]"
    echo ""
    echo "Commands:"
    echo "  start      (default) Clean start: stop + prune + pre-pull + build + run"
    echo "  stop       Stop all services (keeps data volumes)"
    echo "  stop -v    Stop and also remove data volumes (full wipe)"
    echo "  restart    Stop, then clean start"
    echo "  status     Show service status and health checks"
    echo "  pull       Pre-pull base images; add an image name for a single pull"
    echo "  help       Show this help"
}

case "${1:-start}" in
    start)          do_start_with_retry ;;
    stop)
        check_daemon
        if [ "${2:-}" = "-v" ] || [ "${2:-}" = "--volumes" ]; then
            do_stop "yes"
        else
            do_stop "no"
        fi
        ;;
    restart)        do_restart ;;
    status)         show_status ;;
    pull)
        check_daemon
        if [ -n "${2:-}" ]; then
            pull_with_repair "$2" || exit 1
        else
            pull_base_images && echo "[SUCCESS] all base images ready" || exit 1
        fi
        ;;
    help|-h|--help) show_help ;;
    *)
        echo "Unknown option: $1"
        echo ""
        show_help
        exit 1
        ;;
esac
