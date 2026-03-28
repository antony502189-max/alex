#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${1:-.env.deploy}"
BACKUP_ROOT="${2:-./backups}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_PATH="${REPO_ROOT}/${ENV_FILE}"
COMPOSE_FILE="${REPO_ROOT}/docker-compose.deploy.yml"
WAIT_SCRIPT="${SCRIPT_DIR}/wait-for-http.sh"

if [[ ! -f "${ENV_PATH}" ]]; then
  echo "Env file not found: ${ENV_PATH}" >&2
  exit 1
fi

PROJECT_NAME="$(grep '^COMPOSE_PROJECT_NAME=' "${ENV_PATH}" | head -n1 | cut -d'=' -f2- || true)"
if [[ -z "${PROJECT_NAME}" ]]; then
  PROJECT_NAME="alex-prod"
fi

HEALTHCHECK_URL="$(grep '^ALEX_BACKEND_HEALTHCHECK_URL=' "${ENV_PATH}" | head -n1 | cut -d'=' -f2- || true)"
if [[ -z "${HEALTHCHECK_URL}" ]]; then
  HEALTHCHECK_URL="http://localhost:8080/actuator/health"
fi

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_BASE="$(cd "${REPO_ROOT}" && mkdir -p "${BACKUP_ROOT}" && cd "${BACKUP_ROOT}" && pwd)"
BACKUP_DIR="${BACKUP_BASE}/${TIMESTAMP}"
mkdir -p "${BACKUP_DIR}"

VOLUMES=(
  "postgres-data"
  "cassandra-data"
  "minio-data"
  "caddy-data"
  "caddy-config"
  "prometheus-data"
  "alertmanager-data"
  "grafana-data"
)

docker compose --env-file "${ENV_PATH}" -f "${COMPOSE_FILE}" stop

for volume in "${VOLUMES[@]}"; do
  FULL_VOLUME="${PROJECT_NAME}_${volume}"
  docker volume inspect "${FULL_VOLUME}" >/dev/null 2>&1
  docker run --rm \
    -v "${FULL_VOLUME}:/source:ro" \
    -v "${BACKUP_DIR}:/backup" \
    alpine:3.20 \
    sh -c "cd /source && tar -czf /backup/${volume}.tgz ."
done

cat > "${BACKUP_DIR}/manifest.txt" <<EOF
project_name=${PROJECT_NAME}
timestamp=${TIMESTAMP}
env_file=${ENV_FILE}
healthcheck_url=${HEALTHCHECK_URL}
volumes=$(IFS=,; echo "${VOLUMES[*]}")
EOF

docker compose --env-file "${ENV_PATH}" -f "${COMPOSE_FILE}" up -d
"${WAIT_SCRIPT}" "${HEALTHCHECK_URL}" 180

echo "Backup completed: ${BACKUP_DIR}"
