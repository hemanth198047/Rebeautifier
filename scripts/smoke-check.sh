#!/usr/bin/env bash
# Smoke checks for a running Rebeautifier install (default http://localhost:8080).
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
fail=0

check() {
  local name="$1"
  local expect_code="$2"
  local url="$3"
  local body
  local code
  body="$(mktemp)"
  code="$(curl -sS -o "$body" -w "%{http_code}" "$url" || true)"
  if [[ "$code" != "$expect_code" ]]; then
    echo "FAIL  $name — expected HTTP $expect_code, got $code ($url)"
    sed -n '1,5p' "$body" || true
    fail=1
  else
    echo "OK    $name ($code)"
  fi
  rm -f "$body"
}

echo "Smoke checking $BASE_URL"

check "liveness" "200" "$BASE_URL/api/v1/health"
check "readiness" "200" "$BASE_URL/api/v1/health/ready"
check "spa shell" "200" "$BASE_URL/"

# AI status requires auth; unauthenticated should be 401 (endpoint exists).
ai_code="$(curl -sS -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/system/ai-status" || true)"
if [[ "$ai_code" == "401" || "$ai_code" == "200" ]]; then
  echo "OK    ai-status endpoint reachable ($ai_code)"
else
  echo "FAIL  ai-status — expected 401 or 200, got $ai_code"
  fail=1
fi

if [[ "$fail" -ne 0 ]]; then
  echo "Smoke check failed."
  exit 1
fi

echo "Smoke check passed."
