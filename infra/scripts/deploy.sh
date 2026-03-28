#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${1:-.env.deploy}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_PATH="${REPO_ROOT}/${ENV_FILE}"

if [[ ! -f "${ENV_PATH}" ]]; then
  echo "Env file not found: ${ENV_PATH}. Copy .env.deploy.example to .env.deploy first." >&2
  exit 1
fi

docker compose --env-file "${ENV_PATH}" -f "${REPO_ROOT}/docker-compose.deploy.yml" pull
docker compose --env-file "${ENV_PATH}" -f "${REPO_ROOT}/docker-compose.deploy.yml" up -d
docker compose --env-file "${ENV_PATH}" -f "${REPO_ROOT}/docker-compose.deploy.yml" ps

HEALTHCHECK_URL="$(grep '^ALEX_BACKEND_HEALTHCHECK_URL=' "${ENV_PATH}" | head -n1 | cut -d'=' -f2- || true)"
if [[ -z "${HEALTHCHECK_URL}" ]]; then
  HEALTHCHECK_URL="http://localhost:8080/actuator/health"
fi

"${SCRIPT_DIR}/wait-for-http.sh" "${HEALTHCHECK_URL}" 180
