#!/usr/bin/env bash
# Stop Rebeautifier Compose services.
#
# Usage:
#   ./stop.sh                 # stop containers; keep data volumes
#   ./stop.sh --ai            # accepted for compatibility (Ollama always included)
#   ./stop.sh --host-gateway  # match start.sh --host-gateway
#   ./stop.sh --purge         # also delete volumes (db/covers/ollama data)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

PURGE=0
HOST_GATEWAY="${REBEAUTIFIER_HOST_GATEWAY:-0}"

usage() {
  sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --ai) ;; # Ollama is always part of the stack
    --host-gateway) HOST_GATEWAY=1 ;;
    --purge|--volumes|-v) PURGE=1 ;;
    -h|--help) usage 0 ;;
    *)
      echo "Unknown option: $1" >&2
      usage 1
      ;;
  esac
  shift
done

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required but was not found on PATH." >&2
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose)
else
  echo "Docker Compose v2 (docker compose) is required." >&2
  exit 1
fi

FILE_ARGS=(-f docker-compose.yml)
if [[ "$HOST_GATEWAY" == "1" ]]; then
  FILE_ARGS+=(-f docker-compose.host-gateway.yml)
fi

DOWN_ARGS=()
if [[ "$PURGE" -eq 1 ]]; then
  DOWN_ARGS=(-v)
  echo "Stopping Rebeautifier and removing volumes..."
else
  echo "Stopping Rebeautifier (volumes kept)..."
fi

"${COMPOSE[@]}" "${FILE_ARGS[@]}" down "${DOWN_ARGS[@]}"
echo "Stopped."
