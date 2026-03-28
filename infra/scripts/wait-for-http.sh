#!/usr/bin/env bash
set -euo pipefail

URL="${1:?Usage: wait-for-http.sh <url> [timeout-seconds]}"
TIMEOUT_SECONDS="${2:-180}"
DEADLINE=$((SECONDS + TIMEOUT_SECONDS))

while (( SECONDS < DEADLINE )); do
  if curl --silent --show-error --fail --max-time 10 "${URL}" >/dev/null; then
    echo "HTTP health check is ready: ${URL}"
    exit 0
  fi

  sleep 3
done

echo "Timed out waiting for HTTP endpoint: ${URL}" >&2
exit 1
