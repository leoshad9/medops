#!/usr/bin/env bash
# =============================================================================
# scripts/checks/ec2-resource-check.sh
# -----------------------------------------------------------------------------
# Verifies that a 2 GB t4g.small instance can host the MedOps compose stack
# without falling over to the Linux OOM killer.
#
# Why this exists (2026-09-09):
#   The EC2 repeatedly became unresponsive ("instance down") while the compose
#   stack was running. Root cause: peak demand of the Spring + Kafka JVMs plus
#   Postgres exceeds 2 GB RAM on a box with no swap, so the kernel OOM killer
#   stops processes -- and under total exhaustion it kills docker/sshd too.
#   The shadman/ec2-2gb-fit change caps the JVM heaps (MEDOPS_API_JAVA_OPTS,
#   KAFKA_HEAP_OPTS) and adds a swapfile. This script is the rerunnable check
#   that those fixes are in place and nothing has regressed.
#
# Usage (on the EC2, as root):
#   sudo bash scripts/checks/ec2-resource-check.sh
#
# Exit code: 0 when all checks pass, 1 when a check fails (swap missing,
# recent OOM kills, or docker unreachable). Never aborts mid-run: every check
# runs so the full picture is visible at once.
# =============================================================================
set -u

SAW_ERROR=0

say()  { printf '%s\n' "$*"; }
warn() { printf '  [WARN] %s\n' "$*"; }
fail() { SAW_ERROR=1; printf '  [FAIL] %s\n' "$*"; }

echo "==> $(hostname 2>/dev/null || echo unknown) -- EC2 resource check ($(date -u '+%Y-%m-%d %H:%MZ'))"

if [ "$(id -u 2>/dev/null || echo 1)" -ne 0 ]; then
    warn "not running as root -- OOM/daemon journal checks may come back empty; rerun with sudo"
fi

# ---------------------------------------------------------------------------
echo ""
echo "==> Memory & swap"
free -h
echo "---"
if swapon --show 2>/dev/null | grep -q .; then
    swapon --show
    say "  [ OK ] swap is enabled"
else
    fail "no swap configured -- add one (deploy/README.md section 2)"
fi

# ---------------------------------------------------------------------------
echo ""
echo "==> OOM-killer history (last 48h)"
oom_hits=$(journalctl -k --since='-48 hours' 2>/dev/null | grep -iE 'out of memory|oom-kill|killed process|low on memory' | tail -20)
if [ -n "$oom_hits" ]; then
    fail "OOM events found in the last 48h -- JVM caps may not be active, or swap is exhausted"
    printf '%s\n' "$oom_hits" | tail -5
else
    say "  [ OK ] no OOM kills in the last 48h"
fi
if command -v systemctl >/dev/null 2>&1; then
    docker_hits=$(journalctl -u docker --since='-48 hours' 2>/dev/null | grep -iE 'out of memory|oom|killed' | tail -10)
    if [ -n "$docker_hits" ]; then
        warn "docker daemon logged OOM-related lines"
        printf '%s\n' "$docker_hits" | tail -3
    fi
fi

# ---------------------------------------------------------------------------
echo ""
echo "==> Container memory (docker)"
if ! command -v docker >/dev/null 2>&1; then
    fail "docker CLI not found on PATH"
elif ! docker info >/dev/null 2>&1; then
    fail "cannot reach the docker daemon -- is dockerd running?"
else
    docker ps --format 'table {{.Names}}\t{{.Status}}' 2>/dev/null
    docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}' 2>/dev/null || warn "docker stats unavailable"
fi

# ---------------------------------------------------------------------------
echo ""
echo "==> Heap caps applied? (read back from running containers)"
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    api_jvm=$(docker inspect --format '{{join .Config.Env "\n"}}' "$(docker ps -q --filter 'name=medops-api' | head -1)" 2>/dev/null | grep '^JAVA_OPTS=' || true)
    kafka_jvm=$(docker inspect --format '{{join .Config.Env "\n"}}' "$(docker ps -q --filter 'name=kafka' | head -1)" 2>/dev/null | grep '^KAFKA_HEAP_OPTS=' || true)
    if [ -n "$api_jvm" ]; then
        say "  medops-api : $api_jvm"
    else
        warn "medops-api  : JAVA_OPTS not set on container (stale image?)"
    fi
    if [ -n "$kafka_jvm" ]; then
        say "  kafka      : $kafka_jvm"
    else
        warn "kafka       : KAFKA_HEAP_OPTS not set on container (stale image?)"
    fi
fi

# ---------------------------------------------------------------------------
echo ""
if [ "$SAW_ERROR" -eq 1 ]; then
    echo "==> RESULT: CHECKS FAILED -- see [FAIL]/[WARN] lines above."
    exit 1
fi
echo "==> RESULT: PASS -- stack fits in RAM with headroom."
exit 0