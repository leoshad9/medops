# MedOps Deployment Script (PowerShell)
# Local development: start / stop / restart / status / pull
#
# Usage:
#   .\deploy.ps1                     # start (keeps existing DB volumes)
#   .\deploy.ps1 start               # same as above
#   .\deploy.ps1 start -Wipe         # full wipe: stop + remove volumes + prune + build + run
#   .\deploy.ps1 stop                # stop all services (keeps data volumes)
#   .\deploy.ps1 stop -Volumes       # stop and also remove data volumes (full wipe)
#   .\deploy.ps1 restart             # stop then start (keeps DB)
#   .\deploy.ps1 restart -Volumes   # stop + wipe volumes then rebuild
#   .\deploy.ps1 status
#   .\deploy.ps1 pull                # pre-pull all base images (corruption-safe)
#   .\deploy.ps1 pull python:3.14-slim
#   .\deploy.ps1 help
#
# Resilience: detects Docker-Desktop layer corruption (crc32 mismatch / blob not
# found), re-fetches the correct blob from the Docker Hub registry API, verifies
# its sha256, places it into the containerd content store and retries.

param(
    [Parameter(Position = 0)]
    [ValidateSet('start', 'stop', 'restart', 'status', 'pull', 'help')]
    [string]$Action = 'start',

    [Parameter(Position = 1)]
    [string]$Image = '',

    [switch]$Volumes,

    [switch]$Wipe
)

$ErrorActionPreference = "Continue"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

function Show-Banner([string]$Title) {
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host "  MedOps Deploy - $Title" -ForegroundColor Cyan
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host ""
}

function Test-DockerDaemon {
    $null = docker version --format 'ok' 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[FAIL] Docker daemon is not reachable." -ForegroundColor Red
        Write-Host "  Windows: start Docker Desktop and wait until it reports running." -ForegroundColor Yellow
        Write-Host "  Linux:   sudo systemctl start docker" -ForegroundColor Yellow
        return $false
    }
    return $true
}

# Generic retry helper for transient failures (health checks, prune, etc.)
function Invoke-WithRetry {
    param(
        [scriptblock]$ScriptBlock,
        [int]$MaxAttempts = 3,
        [int]$DelaySeconds = 2
    )
    if ($env:DEPLOY_RETRY_MAX_ATTEMPTS) { $MaxAttempts = [int]$env:DEPLOY_RETRY_MAX_ATTEMPTS }
    if ($env:DEPLOY_RETRY_DELAY) { $DelaySeconds = [int]$env:DEPLOY_RETRY_DELAY }
    $attempt = 0
    while ($true) {
        $attempt++
        $result = & $ScriptBlock
        if ($LASTEXITCODE -eq 0) { return $result }
        if ($attempt -ge $MaxAttempts) {
            Write-Host "  - command failed after $attempt attempts" -ForegroundColor Red
            return $result
        }
        Write-Host "  - attempt $attempt/$MaxAttempts failed (exit $LASTEXITCODE), retrying in $DelaySeconds s..." -ForegroundColor Yellow
        Start-Sleep -Seconds $DelaySeconds
    }
}

function Invoke-Compose {
    docker compose -f docker-compose.yml -f docker-compose.prod.yml @args
}

function Get-RepoName([string]$Image) {
    # non-Docker-Hub registries (ghcr.io/..., quay.io/...) are not auto-repairable
    if ($Image -match '^[^/]+\.[^/]+/') { return '' }
    if ($Image -match '/') { return ($Image -replace ':[^/:]*$', '') }
    return 'library/' + ($Image -replace ':[^/:]*$', '')
}

function Repair-CorruptBlob([string]$Image, [string]$Digest) {
    $hex = $Digest -replace '^sha256:', ''
    $repo = Get-RepoName $Image
    if (-not $repo) {
        Write-Host "  - $Image is not from Docker Hub - cannot auto-repair" -ForegroundColor Yellow
        return $false
    }
    Write-Host "  - fetching blob $hex from docker.io/$repo ..." -ForegroundColor Gray
    $tmp = Join-Path $env:TEMP "medops-blob-$hex"
    try {
        $tok = (Invoke-RestMethod "https://auth.docker.io/token?service=registry.docker.io&scope=repository:${repo}:pull").token
        Invoke-WebRequest -Uri "https://registry-1.docker.io/v2/$repo/blobs/sha256:$hex" -Headers @{ Authorization = "Bearer $tok" } -OutFile $tmp -TimeoutSec 1800 -ErrorAction Stop | Out-Null
    } catch {
        Write-Host "  - blob download failed: $($_.Exception.Message)" -ForegroundColor Yellow
        return $false
    }
    $actual = (Get-FileHash $tmp -Algorithm SHA256).Hash.ToLower()
    if ($actual -ne $hex) {
        Write-Host "  - downloaded blob digest mismatch - discarding" -ForegroundColor Yellow
        Remove-Item $tmp -Force -ErrorAction SilentlyContinue
        return $false
    }
    Write-Host "  - blob verified, placing into containerd content store..." -ForegroundColor Gray
    docker run --rm -v "$($env:TEMP):/src:ro" -v /var/lib/desktop-containerd/daemon/io.containerd.content.v1.content/blobs/sha256:/blobs alpine sh -c "cp /src/medops-blob-$hex /blobs/$hex" 2>$null | Out-Null
    docker run --rm -v "$($env:TEMP):/src:ro" -v /var/lib/containerd/io.containerd.content.v1.content/blobs/sha256:/blobs alpine sh -c "cp /src/medops-blob-$hex /blobs/$hex 2>/dev/null; true" 2>$null | Out-Null
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    Write-Host "  - blob placed" -ForegroundColor Green
    return $true
}

function Pull-WithRepair {
    param([string]$Image, [int]$MaxAttempts = 3)
    for ($i = 1; $i -le $MaxAttempts; $i++) {
        Write-Host "  - pulling $Image (attempt $i/$MaxAttempts)..." -ForegroundColor Gray
        $out = (docker pull $Image 2>&1 | Out-String)
        if ($LASTEXITCODE -eq 0) { Write-Host "  [OK] $Image" -ForegroundColor Green; return $true }
        if ($out -match 'crc32 mismatch|corrupted|invalid compressed data|failed to extract layer|blob not found') {
            Write-Host "  [WARN] corrupt/missing layer detected for $Image" -ForegroundColor Yellow
            $digests = [regex]::Matches($out, 'sha256:[0-9a-f]{64}') | ForEach-Object { $_.Value } | Select-Object -Unique
            foreach ($d in $digests) { [void](Repair-CorruptBlob -Image $Image -Digest $d) }
            docker builder prune -af 2>$null | Out-Null
        } else {
            $last = ($out.Trim() -split "`r?`n")[-1]
            Write-Host "  [WARN] pull failed: $last" -ForegroundColor Yellow
            Start-Sleep -Seconds 5
        }
    }
    Write-Host "  [FAIL] could not pull $Image after $MaxAttempts attempts" -ForegroundColor Red
    return $false
}

function Get-BaseImages {
    $images = @()
    Get-ChildItem -Path $ScriptDir -Filter 'Dockerfile' -Recurse -Depth 2 -ErrorAction SilentlyContinue | ForEach-Object {
        foreach ($line in (Get-Content $_.FullName)) {
            if ($line -match '^\s*FROM\s+(?:--\S+\s+)?(\S+)') { $images += $Matches[1] }
        }
    }
    return ($images | Where-Object { $_ -and $_ -ne 'scratch' } | Select-Object -Unique)
}

function Pull-BaseImages {
    $failed = $false
    foreach ($img in (Get-BaseImages)) { if (-not (Pull-WithRepair -Image $img)) { $failed = $true } }
    return (-not $failed)
}

function Stop-AllServices([bool]$Wipe) {
    Show-Banner "Stop"
    Write-Host "[1/1] Stopping all services..." -ForegroundColor Yellow
    if ($Wipe) { Invoke-Compose down -v --remove-orphans 2>$null }
    else { Invoke-Compose down --remove-orphans 2>$null }
    Write-Host "[OK] Services stopped" -ForegroundColor Green
    Write-Host ""
}

function Start-AllServices {
    param([bool]$Wipe)
    if (-not (Test-DockerDaemon)) { return }
    Show-Banner $(if ($Wipe) { "Clean Start (stop -> wipe volumes -> pre-pull -> build -> run)" } else { "Start (stop -> pre-pull -> build -> run)" })

    Write-Host "[0/6] Stopping any existing services..." -ForegroundColor Yellow
    if ($Wipe) { Invoke-Compose down -v --remove-orphans 2>$null }
    else { Invoke-Compose down --remove-orphans 2>$null }
    Write-Host "[OK] Existing services stopped" -ForegroundColor Green
    Write-Host ""

    Write-Host "[1/6] Pruning Docker system (containers, images, build cache)..." -ForegroundColor Yellow
    if ($Wipe) { docker system prune -a -f --volumes }
    else { docker system prune -a -f }
    Write-Host "[OK] Docker system pruned" -ForegroundColor Green
    Write-Host ""

    if ($Wipe) {
        Write-Host "[2/6] Removing remaining data volumes..." -ForegroundColor Yellow
        Invoke-Compose down -v --remove-orphans 2>$null
        Write-Host "[OK] Cleanup complete" -ForegroundColor Green
    } else {
        Write-Host "[2/6] Keeping data volumes (use -Wipe or restart -Volumes to remove)" -ForegroundColor Gray
    }
    Write-Host ""

    Write-Host "[3/6] Setting up certificates..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Force -Path "opt/medops-acme" | Out-Null
    New-Item -ItemType Directory -Force -Path "opt/medops-certs" | Out-Null
    if ((Test-Path "opt/medops-certs/fullchain.pem") -and (Test-Path "opt/medops-certs/privkey.pem")) {
        Write-Host "  - Copying certificates to Docker VM..." -ForegroundColor Gray
        docker run --rm -v /opt/medops-certs:/target -v "${ScriptDir}/opt/medops-certs:/source:ro" alpine sh -c 'cp -r /source/* /target/' 2>$null
    }
    Write-Host "[OK] Certificate directories ready" -ForegroundColor Green
    Write-Host ""

    Write-Host "[4/6] Pre-pulling base images (corruption-safe)..." -ForegroundColor Yellow
    if (Pull-BaseImages) { Write-Host "[OK] Base images ready" -ForegroundColor Green }
    else { Write-Host "[WARN] Some base images failed to pull - build may still recover" -ForegroundColor Yellow }
    Write-Host ""

    Write-Host "[5/6] Building and starting all services..." -ForegroundColor Yellow
    $buildOk = $false
    for ($attempt = 1; $attempt -le 2; $attempt++) {
        $out = (Invoke-Compose --env-file .env up -d --build 2>&1 | ForEach-Object { Write-Host $_; "$_" })
        if ($LASTEXITCODE -eq 0) { $buildOk = $true; break }
        if ($out -match 'crc32 mismatch|corrupted|failed to extract layer|blob not found') {
            Write-Host "[WARN] corruption detected during build - repairing and retrying..." -ForegroundColor Yellow
            $digests = [regex]::Matches(($out | Out-String), 'sha256:[0-9a-f]{64}') | ForEach-Object { $_.Value } | Select-Object -Unique
            foreach ($d in $digests) {
                foreach ($img in (Get-BaseImages)) { if (Repair-CorruptBlob -Image $img -Digest $d) { break } }
            }
            docker builder prune -af 2>$null | Out-Null
        } else {
            Write-Host "[FAIL] build failed - see output above" -ForegroundColor Red
            break
        }
    }
    if (-not $buildOk) { Show-Status; return $false }
    Write-Host "[OK] Services starting..." -ForegroundColor Green
    Write-Host ""

    Write-Host "[6/6] Waiting for services to be healthy..." -ForegroundColor Yellow
    Start-Sleep -Seconds 15
    Show-Status
    return $true
}

function Start-AllServicesWithRetry {
    param([bool]$Wipe)
    $attempt = 1
    $maxAttempts = if ($env:DEPLOY_MAX_ATTEMPTS) { [int]$env:DEPLOY_MAX_ATTEMPTS } else { 2 }

    while ($true) {
        Write-Host ""
        Write-Host "==========================================" -ForegroundColor Cyan
        Write-Host "==> DEPLOY ATTEMPT $attempt OF $maxAttempts" -ForegroundColor Cyan
        Write-Host "==========================================" -ForegroundColor Cyan
        $ok = Start-AllServices -Wipe:$Wipe
        if ($ok) {
            Write-Host ""
            Write-Host "==> Deploy succeeded on attempt $attempt" -ForegroundColor Green
            return
        }
        $attempt++
        if ($attempt -gt $maxAttempts) {
            Write-Host ""
            Write-Host "==> [FAIL] deploy failed after $maxAttempts attempts" -ForegroundColor Red
            return
        }
        Write-Host "==> Restarting from scratch in 10s..." -ForegroundColor Yellow
        Stop-AllServices $Wipe | Out-Null
        Start-Sleep -Seconds 10
    }
}

function Show-Status {
    if (-not (Test-DockerDaemon)) { return }
    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Cyan
    Write-Host "  Service Status" -ForegroundColor Cyan
    Write-Host "==========================================" -ForegroundColor Cyan
    Invoke-Compose ps
    Write-Host ""

    $Running = (Invoke-Compose ps --quiet | Measure-Object).Count
    $Total = (Invoke-Compose config --services | Measure-Object).Count

    if ($Running -eq $Total -and $Total -gt 0) {
        Write-Host "[SUCCESS] All $Total services are running!" -ForegroundColor Green
    } else {
        Write-Host "[WARN] $Running/$Total services running. Check logs with:" -ForegroundColor Yellow
        Write-Host "   docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f" -ForegroundColor Gray
    }
    Write-Host ""

    Write-Host "Health checks:" -ForegroundColor Cyan
    $aiCode  = Invoke-WithRetry { & curl.exe -sf -o NUL --max-time 5 -w '%{http_code}' http://localhost:8000/health } 5 2
    $apiCode = Invoke-WithRetry { & curl.exe -sf -o NUL --max-time 5 -w '%{http_code}' http://localhost:8080/actuator/health } 5 2
    $uiCode  = Invoke-WithRetry { & curl.exe -sfk -o NUL --max-time 5 -w '%{http_code}' https://localhost/ } 5 2
    Write-Host "  - AI  :8000  HTTP $aiCode"
    Write-Host "  - API :8080  HTTP $apiCode"
    Write-Host "  - UI  :443   HTTP $uiCode"
    Write-Host ""

    Write-Host "Access points:" -ForegroundColor Cyan
    Write-Host "  - UI (HTTP):  http://localhost" -ForegroundColor White
    Write-Host "  - UI (HTTPS): https://localhost" -ForegroundColor White
    Write-Host "  - AI Service: http://localhost:8000/health" -ForegroundColor White
    Write-Host "  - API:        http://localhost:8080/actuator/health" -ForegroundColor White
    Write-Host ""
}

function Show-Help {
    Write-Host "MedOps Deployment Script"
    Write-Host ""
    Write-Host "Usage: .\deploy.ps1 [command] [image] [-Volumes] [-Wipe]"
    Write-Host "Commands:"
    Write-Host "  start      (default) Start: stop + prune + pre-pull + build + run (keeps DB volumes)" -ForegroundColor White
    Write-Host "  start -Wipe        Full restart: stop + remove data volumes + prune + build + run" -ForegroundColor White
    Write-Host "  stop -Volumes  Stop and also remove data volumes (full wipe)" -ForegroundColor White
    Write-Host "  restart    Stop, then start (keeps DB)" -ForegroundColor White
    Write-Host "  restart -Volumes  Stop + wipe volumes then rebuild" -ForegroundColor White
    Write-Host "  status     Show service status and health checks"
    Write-Host "  pull       Pre-pull base images; add an image name for a single pull"
    Write-Host "  help       Show this help"
}

switch ($Action) {
    'start'   { Start-AllServicesWithRetry -Wipe:$Wipe }
    'stop'    { if (-not (Test-DockerDaemon)) { exit 1 }; Stop-AllServices $Volumes }
    'restart' { Stop-AllServices $Volumes; Start-AllServicesWithRetry -Wipe:$Volumes.IsPresent }
    'status'  { Show-Status }
    'pull'    {
        if (-not (Test-DockerDaemon)) { exit 1 }
        if ($Image) { if (Pull-WithRepair -Image $Image) { exit 0 } else { exit 1 } }
        elseif (Pull-BaseImages) { Write-Host "[SUCCESS] all base images ready" -ForegroundColor Green; exit 0 }
        else { exit 1 }
    }
    'help'    { Show-Help }
}
