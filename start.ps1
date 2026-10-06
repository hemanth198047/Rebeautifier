# Start Rebeautifier (Postgres + API + SPA) via Docker Compose.
#
# Usage:
#   .\start.ps1
#   .\start.ps1 -Ai
#   .\start.ps1 -Ai -PullModel
#   .\start.ps1 -HostGateway
#   .\start.ps1 -NoRebuild

[CmdletBinding()]
param(
    [switch]$Ai,
    [switch]$PullModel,
    [switch]$NoRebuild,
    [switch]$HostGateway
)

$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

function Test-DockerCompose {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "Docker is required but was not found on PATH. Install Docker Desktop for Windows, start it, then retry."
    }
    docker compose version 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Docker Compose v2 "docker compose" is required.'
    }
}

Test-DockerCompose

if (-not (Test-Path '.env') -and (Test-Path '.env.example')) {
    Copy-Item '.env.example' '.env'
    Write-Host 'Created .env from .env.example (edit passwords for production).'
}

$apiPort = '8080'
if (Test-Path '.env') {
    Get-Content '.env' | ForEach-Object {
        if ($_ -match '^\s*API_PORT\s*=\s*(.+)\s*$') { $apiPort = $Matches[1].Trim() }
    }
}
$baseUrl = "http://localhost:$apiPort"

$composeArgs = @('-f', 'docker-compose.yml')
if ($HostGateway -or $env:REBEAUTIFIER_HOST_GATEWAY -eq '1') {
    if (-not (Test-Path 'docker-compose.host-gateway.yml')) {
        throw 'docker-compose.host-gateway.yml is missing.'
    }
    $composeArgs += @('-f', 'docker-compose.host-gateway.yml')
    Write-Host 'Using host-gateway Compose override (nested Docker / broken bridge).'
}
if ($Ai) { $composeArgs += @('--profile', 'ai') }
$composeArgs += 'up'
if (-not $NoRebuild) { $composeArgs += '--build' }
$composeArgs += '-d'

Write-Host 'Starting Rebeautifier (Compose)...'
& docker compose @composeArgs
if ($LASTEXITCODE -ne 0) { throw 'Failed to start Compose services.' }

Write-Host "Waiting for API readiness at $baseUrl/api/v1/health/ready ..."
$ready = $false
for ($i = 0; $i -lt 90; $i++) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/api/v1/health/ready" -TimeoutSec 3
        if ($response.StatusCode -eq 200) {
            $ready = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 2
    }
}

if (-not $ready) {
    Write-Host 'Timed out waiting for readiness. Recent logs:'
    $logArgs = @('-f', 'docker-compose.yml')
    if ($HostGateway -or $env:REBEAUTIFIER_HOST_GATEWAY -eq '1') {
        $logArgs += @('-f', 'docker-compose.host-gateway.yml')
    }
    if ($Ai) { $logArgs += @('--profile', 'ai') }
    & docker compose @logArgs logs --tail=80 api db
    if (-not ($HostGateway -or $env:REBEAUTIFIER_HOST_GATEWAY -eq '1')) {
        Write-Host 'If containers cannot reach each other, retry: .\start.ps1 -HostGateway'
    }
    throw 'API readiness timed out.'
}

if ($Ai -and $PullModel) {
    $model = 'llama3.2:1b'
    if (Test-Path '.env') {
        Get-Content '.env' | ForEach-Object {
            if ($_ -match '^\s*OLLAMA_MODEL\s*=\s*(.+)\s*$') { $model = $Matches[1].Trim() }
        }
    }
    Write-Host "Pulling Ollama model $model (may take a while)..."
    $pullArgs = @('-f', 'docker-compose.yml')
    if ($HostGateway -or $env:REBEAUTIFIER_HOST_GATEWAY -eq '1') {
        $pullArgs += @('-f', 'docker-compose.host-gateway.yml')
    }
    $pullArgs += @('--profile', 'ai', 'exec', '-T', 'ollama', 'ollama', 'pull', $model)
    & docker compose @pullArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Warning 'Model pull failed; AI will show offline until the model is available.'
    }
}

Write-Host ''
Write-Host 'Rebeautifier is up.'
Write-Host "  Open:     $baseUrl"
Write-Host '  Demo:     reader / password123'
Write-Host "  Health:   $baseUrl/api/v1/health"
$stopCmd = '.\stop.ps1'
if ($Ai) { $stopCmd += ' -Ai' }
if ($HostGateway -or $env:REBEAUTIFIER_HOST_GATEWAY -eq '1') { $stopCmd += ' -HostGateway' }
Write-Host "  Stop:     $stopCmd"
if (-not $Ai) {
    Write-Host '  With AI:  .\start.ps1 -Ai -PullModel'
}
Write-Host ''
