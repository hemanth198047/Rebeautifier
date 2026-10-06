#!/usr/bin/env bash
# Start Rebeautifier (Postgres + API + SPA) via Docker Compose.
#
# Usage:
#   ./start.sh              # core stack (db + api)
#   ./start.sh --ai         # also start Ollama profile
#   ./start.sh --ai --pull-model   # start AI and pull default model
#   ./start.sh --host-gateway      # nested-Docker / broken bridge networking
#   ./start.sh --foreground # attach to compose logs
#   ./start.sh --rebuild    # force image rebuild (default)
#   ./start.sh --no-rebuild
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

WITH_AI=0
PULL_MODEL=0
FOREGROUND=0
REBUILD=1
HOST_GATEWAY="${REBEAUTIFIER_HOST_GATEWAY:-0}"
DETACH_ARGS=(-d)

usage() {
  sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --ai) WITH_AI=1 ;;
    --pull-model) PULL_MODEL=1 ;;
    --host-gateway) HOST_GATEWAY=1 ;;
    --foreground|-f) FOREGROUND=1; DETACH_ARGS=() ;;
    --rebuild) REBUILD=1 ;;
    --no-rebuild) REBUILD=0 ;;
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
  echo "Install Docker Desktop / Docker Engine, then retry." >&2
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

if [[ ! -f .env && -f .env.example ]]; then
  cp .env.example .env
  echo "Created .env from .env.example (edit passwords for production)."
fi

FILE_ARGS=(-f docker-compose.yml)
if [[ "$HOST_GATEWAY" == "1" ]]; then
  if [[ ! -f docker-compose.host-gateway.yml ]]; then
    echo "docker-compose.host-gateway.yml is missing." >&2
    exit 1
  fi
  FILE_ARGS+=(-f docker-compose.host-gateway.yml)
  echo "Using host-gateway Compose override (nested Docker / broken bridge)."
fi

PROFILE_ARGS=()
if [[ "$WITH_AI" -eq 1 ]]; then
  PROFILE_ARGS=(--profile ai)
fi

BUILD_ARGS=()
if [[ "$REBUILD" -eq 1 ]]; then
  BUILD_ARGS=(--build)
fi

API_PORT="$(grep -E '^[[:space:]]*API_PORT=' .env 2>/dev/null | tail -n1 | cut -d= -f2- | tr -d '\r' || true)"
API_PORT="${API_PORT:-8080}"
BASE_URL="http://localhost:${API_PORT}"

echo "Starting Rebeautifier (Compose)…"
"${COMPOSE[@]}" "${FILE_ARGS[@]}" "${PROFILE_ARGS[@]}" up "${BUILD_ARGS[@]}" "${DETACH_ARGS[@]}"

if [[ "$FOREGROUND" -eq 1 ]]; then
  exit 0
fi

echo "Waiting for API readiness at ${BASE_URL}/api/v1/health/ready …"
ready=0
for _ in $(seq 1 90); do
  code="$(curl -sS -o /dev/null -w '%{http_code}' "${BASE_URL}/api/v1/health/ready" 2>/dev/null || true)"
  if [[ "$code" == "200" ]]; then
    ready=1
    break
  fi
  sleep 2
done

if [[ "$ready" -ne 1 ]]; then
  echo "Timed out waiting for readiness. Recent logs:" >&2
  "${COMPOSE[@]}" "${FILE_ARGS[@]}" "${PROFILE_ARGS[@]}" logs --tail=80 api db || true
  if [[ "$HOST_GATEWAY" != "1" ]]; then
    echo >&2
    echo "If containers cannot reach each other (nested Docker / cloud agents), retry:" >&2
    echo "  ./start.sh --host-gateway$([ "$WITH_AI" -eq 1 ] && echo ' --ai')" >&2
  fi
  exit 1
fi

if [[ "$WITH_AI" -eq 1 && "$PULL_MODEL" -eq 1 ]]; then
  MODEL="$(grep -E '^[[:space:]]*OLLAMA_MODEL=' .env 2>/dev/null | tail -n1 | cut -d= -f2- | tr -d '\r' || true)"
  MODEL="${MODEL:-llama3.2:1b}"
  echo "Pulling Ollama model ${MODEL} (may take a while)…"
  "${COMPOSE[@]}" "${FILE_ARGS[@]}" --profile ai exec -T ollama ollama pull "$MODEL" || {
    echo "Model pull failed; AI will show offline until the model is available." >&2
  }
fi

STOP_EXTRA=""
[[ "$WITH_AI" -eq 1 ]] && STOP_EXTRA+=" --ai"
[[ "$HOST_GATEWAY" == "1" ]] && STOP_EXTRA+=" --host-gateway"

echo
echo "Rebeautifier is up."
echo "  Open:     ${BASE_URL}"
echo "  Demo:     reader / password123"
echo "  Health:   ${BASE_URL}/api/v1/health"
echo "  Stop:     ./stop.sh${STOP_EXTRA}"
if [[ "$WITH_AI" -eq 0 ]]; then
  echo "  With AI:  ./start.sh --ai --pull-model"
fi
echo
