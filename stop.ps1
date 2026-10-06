# Stop Rebeautifier Compose services.
#
# Usage:
#   .\stop.ps1
#   .\stop.ps1 -Ai
#   .\stop.ps1 -Purge

[CmdletBinding()]
param(
    [switch]$Ai,
    [switch]$Purge,
    [switch]$HostGateway
)

$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Docker is required but was not found on PATH.'
}
docker compose version 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Compose v2 "docker compose" is required.'
}

$composeArgs = @('-f', 'docker-compose.yml')
if ($HostGateway -or $env:REBEAUTIFIER_HOST_GATEWAY -eq '1') {
    $composeArgs += @('-f', 'docker-compose.host-gateway.yml')
}
if ($Ai) { $composeArgs += @('--profile', 'ai') }
$composeArgs += 'down'
if ($Purge) {
    $composeArgs += '-v'
    Write-Host 'Stopping Rebeautifier and removing volumes…'
} else {
    Write-Host 'Stopping Rebeautifier (volumes kept)…'
}

& docker compose @composeArgs
if ($LASTEXITCODE -ne 0) { throw 'Failed to stop Compose services.' }

Write-Host 'Stopped.'
