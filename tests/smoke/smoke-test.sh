#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TEST_USERNAME="${TEST_USERNAME:-test}"
TEST_PASSWORD="${TEST_PASSWORD:-test}"
MAX_ATTEMPTS="${SMOKE_MAX_ATTEMPTS:-5}"
REQUEST_TIMEOUT_SEC="${SMOKE_TIMEOUT_SEC:-10}"
SLEEP_BETWEEN_RETRY_SEC="${SMOKE_RETRY_DELAY_SEC:-2}"
LAST_BODY=""
TOKEN=""

log() {
  printf '[smoke] %s\n' "$1"
}

request_with_retry() {
  local method="$1"
  local endpoint="$2"
  local expected_csv="$3"
  local description="$4"
  local payload="${5:-}"
  local extra_header="${6:-}"

  local attempt=1
  local response_code="000"
  local request_url="${BASE_URL}${endpoint}"
  local response_file
  response_file="$(mktemp)"
  trap 'rm -f "$response_file"' RETURN

  while [[ "$attempt" -le "$MAX_ATTEMPTS" ]]; do
    log "${description} (attempt ${attempt}/${MAX_ATTEMPTS})"

    local curl_args
    curl_args=(
      -sS
      --max-time "$REQUEST_TIMEOUT_SEC"
      -X "$method"
      "$request_url"
      -o "$response_file"
      -w '%{http_code}'
    )

    if [[ -n "$extra_header" ]]; then
      curl_args+=( -H "$extra_header" )
    fi

    if [[ -n "$payload" ]]; then
      curl_args+=( -H 'Content-Type: application/json' --data "$payload" )
    fi

    response_code=$(curl "${curl_args[@]}" || true)

    IFS=',' read -r -a expected_codes <<< "$expected_csv"
    for expected in "${expected_codes[@]}"; do
      if [[ "$response_code" == "$expected" ]]; then
        LAST_BODY="$(cat "$response_file")"
        log "PASS: ${description} -> HTTP ${response_code}"
        return 0
      fi
    done

    log "WARN: ${description} returned HTTP ${response_code}, expected one of [${expected_csv}]"
    if [[ "$attempt" -lt "$MAX_ATTEMPTS" ]]; then
      sleep "$SLEEP_BETWEEN_RETRY_SEC"
    fi
    attempt=$((attempt + 1))
  done

  log "FAIL: ${description} did not pass after ${MAX_ATTEMPTS} attempts"
  return 1
}

extract_token() {
  local response_json="$1"
  printf '%s' "$response_json" | python3 -c "import json,sys
try:
    data=json.load(sys.stdin)
except Exception:
    sys.exit(2)
token=(
    data.get('token')
    or (data.get('data') or {}).get('token')
    or data.get('accessToken')
    or (data.get('data') or {}).get('accessToken')
)
if not token:
    sys.exit(1)
print(token, end='')"
}

log "Starting smoke test against ${BASE_URL}"

request_with_retry "GET" "/actuator/health" "200" "Health endpoint"

LOGIN_PAYLOAD=$(printf '{"username":"%s","password":"%s"}' "$TEST_USERNAME" "$TEST_PASSWORD")
request_with_retry "POST" "/api/auth/login" "200,201" "Authentication endpoint" "$LOGIN_PAYLOAD"

if ! echo "$LAST_BODY" | python3 -m json.tool > /dev/null 2>&1; then
  log "FAIL: login response is not valid JSON"
  exit 1
fi

if ! TOKEN="$(extract_token "$LAST_BODY")"; then
  log "FAIL: Unable to extract JWT token from login response"
  exit 1
fi

if [[ -z "$TOKEN" ]]; then
  log "FAIL: extracted token is empty"
  exit 1
fi

request_with_retry "GET" "/api/dashboard" "200" "Main business endpoint" "" "Authorization: Bearer ${TOKEN}"

log "Smoke test completed successfully"
